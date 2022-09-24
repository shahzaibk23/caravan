package caravan.bus.tilelink
import caravan.bus.common.{AddressMap, BusDecoder, DeviceAdapter, Switch1toN, DummyMemController}
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util.{Cat, MuxLookup}
import chisel3.util.experimental.loadMemoryFromFile


class Harness/*(programFile: Option[String])*/(implicit val config: TilelinkConfig) extends MultiIOModule {
    val valid = Input(Bool())
    val addrReq = Input(UInt(config.a.W))
    val dataReq = Input(UInt((config.w * 8).W))
    val byteLane = Input(UInt(config.w.W))
    val isWrite = Input(Bool())

    val validResp = Output(Bool())
    val dataResp = Output(UInt(32.W))
    // val ackResp = Output(Bool())

  implicit val request = new TLRequest()    
  implicit val response = new TLResponse()

  val tlHost = Module(new TilelinkHost())
  val tlSlave = Module(new TilelinkDevice())
  val memCtrl = Module(new DummyMemController())

  tlHost.rspOut.ready := true.B  // IP always ready to accept data from wb host

  tlHost.tlMasterTransmitter <> tlSlave.tlMasterReceiver
  tlSlave.tlSlaveTransmitter <> tlHost.tlSlaveReceiver

  tlHost.reqIn.valid := Mux(tlHost.reqIn.ready, valid, false.B)
  tlHost.reqIn.bits.addrRequest := addrReq
  tlHost.reqIn.bits.dataRequest := dataReq
  tlHost.reqIn.bits.activeByteLane := byteLane
  tlHost.reqIn.bits.isWrite := isWrite
  


  tlSlave.reqOut <> memCtrl.req
  tlSlave.rspIn <> memCtrl.rsp

  dataResp := tlHost.rspOut.bits.dataResponse
  validResp := tlHost.rspOut.valid
  // io.ackResp := tlHost.rspOut.bits.ackWrite

}


