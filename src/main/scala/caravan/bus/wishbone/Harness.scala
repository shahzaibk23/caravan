package caravan.bus.wishbone
import java.math.BigInteger

import caravan.bus.common.{AddressMap, BusDecoder, Switch1toN}
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util.{Cat, Decoupled, DecoupledIO, MuxLookup, log2Ceil}
import chisel3.util.experimental.loadMemoryFromFile

object Peripherals extends ChiselEnum {
  val DCCM = Value(0.U)
  val GPIO = Value(1.U)
}

class Harness(programFile: String)(implicit val config: WishboneConfig) extends Module {
  val io = IO(new Bundle {
    val valid = Input(Bool())
    val addrReq = Input(UInt(config.addressWidth.W))
    val dataReq = Input(UInt(config.dataWidth.W))
    val byteLane = Input(UInt((config.dataWidth/config.granularity).W))
    val isWrite = Input(Bool())

    val validResp = Output(Bool())
    val dataResp = Output(UInt(32.W))
  })

  val wbHost = Module(new WishboneHost())
  val wbSlave = Module(new WishboneDevice())
  val memCtrl = Module(new DummyMemController(programFile))

  wbHost.io.rspOut.ready := true.B  // IP always ready to accept data from wb host

  wbHost.io.wbMasterTransmitter <> wbSlave.io.wbMasterReceiver
  wbSlave.io.wbSlaveTransmitter <> wbHost.io.wbSlaveReceiver

  wbHost.io.reqIn.valid := Mux(wbHost.io.reqIn.ready, io.valid, false.B)
  wbHost.io.reqIn.bits.addrRequest := io.addrReq
  wbHost.io.reqIn.bits.dataRequest := io.dataReq
  wbHost.io.reqIn.bits.activeByteLane := io.byteLane
  wbHost.io.reqIn.bits.isWrite := io.isWrite



  wbSlave.io.reqOut <> memCtrl.io.req
  wbSlave.io.rspIn <> memCtrl.io.rsp

  io.dataResp := wbHost.io.rspOut.bits.dataResponse
  io.validResp := wbHost.io.rspOut.valid

}

class SwitchHarness(programFile: String)(implicit val config: WishboneConfig) extends Module {
  val io = IO(new Bundle {
    val valid = Input(Bool())
    val addrReq = Input(UInt(config.addressWidth.W))
    val dataReq = Input(UInt(config.dataWidth.W))
    val byteLane = Input(UInt((config.dataWidth/config.granularity).W))
    val isWrite = Input(Bool())

    val validResp = Output(Bool())
    val dataResp = Output(UInt(32.W))
    val errResp = Output(Bool())
  })

  val addressMap = new AddressMap
  addressMap.addDevice(Peripherals.DCCM, "h40000000".U(32.W), "h00000fff".U(32.W))
  addressMap.addDevice(Peripherals.GPIO, "h40001000".U(32.W), "h00000fff".U(32.W))

  val devicesNum = addressMap.getMap().size

  val host = Module(new WishboneHost())
  val dccmDev = Module(new WishboneDevice())
  val gpioDev = Module(new WishboneDevice())
  val memCtrl = Module(new DummyMemController(programFile))
  val gpioCtrl = Module(new DummyGpioController())
  val switch = Module(new Switch1toN[WBHost, WBDevice](new WishboneMaster(), new WishboneSlave(), devicesNum))
  val wbErr = Module(new WishboneErr())

  val devices = Seq(dccmDev, gpioDev)

  host.io.rspOut.ready := true.B  // IP always ready to accept data from wb host
  host.io.reqIn.valid := Mux(host.io.reqIn.ready, io.valid, false.B)
  host.io.reqIn.bits.addrRequest := io.addrReq
  host.io.reqIn.bits.dataRequest := io.dataReq
  host.io.reqIn.bits.activeByteLane := io.byteLane
  host.io.reqIn.bits.isWrite := io.isWrite

  switch.io.hostIn <> host.io.wbMasterTransmitter
  switch.io.hostOut <> host.io.wbSlaveReceiver


  for (i <- 0 until devicesNum) {
    switch.io.devOut(i) <> devices(i).io.wbMasterReceiver
    switch.io.devIn(i) <> devices(i).io.wbSlaveTransmitter
  }
  switch.io.devOut(devicesNum) <> wbErr.io.wbMasterReceiver
  switch.io.devIn(devicesNum) <> wbErr.io.wbSlaveTransmitter

  switch.io.devSel := BusDecoder.decode(host.io.wbMasterTransmitter.bits.adr, addressMap)
  dccmDev.io.reqOut <> memCtrl.io.req
  dccmDev.io.rspIn <> memCtrl.io.rsp

  gpioDev.io.reqOut <> gpioCtrl.io.req
  gpioDev.io.rspIn <> gpioCtrl.io.rsp

  io.dataResp := host.io.rspOut.bits.dataResponse
  io.validResp := host.io.rspOut.valid
  io.errResp := host.io.rspOut.bits.error

}

class DummyMemController(programFile: String)(implicit val config: WishboneConfig) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new Request()))
    val rsp = Decoupled(new Response())
  })
  // the register that sends valid along with the data read from memory
  // a register is used so that it synchronizes along with the data that comes after one cycle
  val validReg = RegInit(false.B)
  io.rsp.valid := validReg
  io.rsp.bits.error := false.B   // assuming memory controller would never return an error
  io.req.ready := true.B // always ready to accept requests from device
  val mem = SyncReadMem(1024, UInt(32.W))
  loadMemoryFromFile(mem, programFile)
  when(io.req.valid && !io.req.bits.isWrite) {
    when(io.req.bits.activeByteLane === "b0001".U) {
      io.rsp.bits.dataResponse := Cat(0.U(24.W), mem.read(io.req.bits.addrRequest/4.U)(7,0))
      validReg := true.B
    } .elsewhen(io.req.bits.activeByteLane === "b0011".U) {
      io.rsp.bits.dataResponse := Cat(0.U(16.W), mem.read(io.req.bits.addrRequest/4.U)(15,0))
      validReg := true.B
    } .elsewhen(io.req.bits.activeByteLane === "b0111".U) {
      io.rsp.bits.dataResponse := Cat(0.U(8.W), mem.read(io.req.bits.addrRequest/4.U)(23,0))
      validReg := true.B
    } .elsewhen(io.req.bits.activeByteLane === "b1111".U) {
      io.rsp.bits.dataResponse := mem.read(io.req.bits.addrRequest/4.U)
      validReg := true.B
    } .otherwise {
      io.rsp.bits.dataResponse := DontCare
      validReg := false.B
    }
  } .elsewhen(io.req.valid && io.req.bits.isWrite) {
    mem.write(io.req.bits.addrRequest/4.U, io.req.bits.dataRequest)
    validReg := true.B
    io.rsp.bits.dataResponse := DontCare
  }. otherwise {
    validReg := false.B
    io.rsp.bits.dataResponse := DontCare
  }
}

class DummyGpioController(implicit val config: WishboneConfig) extends Module {
  val io = IO(new Bundle {
    val req = Flipped(Decoupled(new Request()))
    val rsp = Decoupled(new Response())
  })

  val addr_wire = io.req.bits.addrRequest

  val err_rsp_wire = WireInit(false.B)
  val data_rsp_wire = Wire(UInt(config.dataWidth.W))
  val valid_rsp_wire = WireInit(false.B)

  data_rsp_wire := DontCare

  val errReg = RegInit(false.B)
  val dataReg = RegInit(0.U(config.dataWidth.W))
  val validReg = RegInit(false.B)

  object GpioRegisters extends ChiselEnum {
    val OUTPUT_EN_REG = Value(0.U)
    val WDATA_REG = Value(4.U)
    val RDATA_REG = Value(8.U)
  }

  def isRegisterFound(addr: UInt): Bool = {
    GpioRegisters.all.map(g => g.asUInt === addr).reduce((a,b) => a || b)
  }


  /** FIXME: Assuming GPIO will always have less than 64 registers available
   * that is why taking 6 bits wire for addressing */
  val offset = Wire(UInt(6.W))   // 6 bit wire

  offset := io.req.bits.addrRequest

  io.req.ready := true.B    // always ready to accept req from the bus

  val registers = RegInit(VecInit(Seq.fill(GpioRegisters.all.size)(0.U(32.W))))

  when(io.req.fire() && io.req.bits.isWrite) {
    // WRITE
    valid_rsp_wire := true.B
    when(isRegisterFound(offset)) {
      // correct address for a register found
      val accessed_reg = registers(offset/4.U)
      accessed_reg := io.req.bits.dataRequest
    } .otherwise {
      // no correct address found, send an error response
      err_rsp_wire := true.B
    }
  } .elsewhen(io.req.fire() && !io.req.bits.isWrite) {
    // READ
    valid_rsp_wire := true.B
    when(isRegisterFound(offset)) {
      val accessed_reg = registers(offset/4.U)
      data_rsp_wire := accessed_reg
    } .otherwise {
      err_rsp_wire := true.B
    }

  }

  validReg := valid_rsp_wire
  errReg := err_rsp_wire
  dataReg := data_rsp_wire

  io.rsp.valid := validReg
  io.rsp.bits.error := errReg
  io.rsp.bits.dataResponse := dataReg

}

object DummyGpioControllerDriver extends App {
  implicit val config = WishboneConfig(addressWidth = 10, dataWidth = 32)
  println((new ChiselStage).emitVerilog(new DummyGpioController()))
}

object SwitchHarnessDriver extends App {
  implicit val config = WishboneConfig(10, 32)
  println((new ChiselStage).emitVerilog(new SwitchHarness("/Users/mbp/Desktop/mem1.txt")))
}

object HarnessDriver extends App {
  implicit val config = WishboneConfig(addressWidth = 10, dataWidth = 32)
  println((new ChiselStage).emitVerilog(new Harness("/Users/mbp/Desktop/mem1.txt")))
}
