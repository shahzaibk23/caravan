package caravan.bus.tilelink
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.{ Fill}
// import caravan.bus.common.DeviceAdapter

abstract class TLErr extends MultiIOModule
class TilelinkError(implicit val config: TilelinkConfig) extends TLErr {
    val tlSlaveTransmitter = IO(DecoupledMulti(new TilelinkSlave()))
    val tlMasterReceiver = IO(Flipped(DecoupledMulti(new TilelinkMaster())))

  // def fire(): Bool = tlMasterReceiver.valid && tlMasterReceiver.bits.cyc && tlMasterReceiver.bits.stb

  val ackReg = RegInit(true.B)
  val dataReg = RegInit(0.U)
  val errReg = RegInit(false.B)
  val validReg = RegInit(false.B)
  val opCodeReg = RegInit(Mux(tlMasterReceiver.bits.a_opcode === 4.U, 1.U, 0.U))
  val paramReg = RegInit(0.U)
  val sizeReg = RegInit(tlMasterReceiver.bits.a_size)

  /** FIXME: Assuming tilelink slave is always ready to accept master req */
  tlMasterReceiver.ready := true.B

  when(tlMasterReceiver.fire()) {
    // a valid request from the host. The decoder pointed to us which means there was a wrong address given by the user
    // for writes we are going to ignore them completely.
    // for reads we are going to signal an err out and send all FFFs.
    errReg := true.B
    validReg := true.B
    when(tlMasterReceiver.bits.a_opcode === 0.U || tlMasterReceiver.bits.a_opcode === 1.U) {
      // WRITE
      dataReg := DontCare
    } .elsewhen(tlMasterReceiver.bits.a_opcode === 4.U) {
      // READ
      dataReg := Fill((config.w * 8)/4, "hf".U)
    }

  } .otherwise {
      // no valid request from the host
    dataReg := 0.U
    errReg := false.B
    validReg := false.B
  }

  tlSlaveTransmitter.valid := validReg
  tlSlaveTransmitter.bits.d_denied := errReg
  tlSlaveTransmitter.bits.d_data := dataReg
  tlSlaveTransmitter.bits.d_corrupt := errReg

  tlSlaveTransmitter.bits.d_opcode := opCodeReg
  tlSlaveTransmitter.bits.d_param := paramReg
  tlSlaveTransmitter.bits.d_size := sizeReg
  tlSlaveTransmitter.bits.d_source := paramReg // TODO: Add dynamic logic for source
  tlSlaveTransmitter.bits.d_sink := paramReg

}

// object TilelinkErrDriver extends App {
//   implicit val config = TilelinkConfig()
//   println("-------------------------------- -------- ------------ -------------------")
//   println((new ChiselStage).emitVerilog(new TilelinkError()))
// }
