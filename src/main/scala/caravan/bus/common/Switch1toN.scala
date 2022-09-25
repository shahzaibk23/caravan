package caravan.bus.common
import caravan.bus.wishbone.{WBDevice, WBHost, WishboneConfig, WishboneMaster, WishboneSlave}
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util.{log2Ceil}

class Switch1toN[A <: BusHost, B <: BusDevice](mb: A, sb: B, N: Int) extends MultiIOModule {
    val hostIn = Module(Flipped(new DecoupledMulti(new mb)))
    val hostOut = Module(new DecoupledMulti(new sb))
    val devOut = Module(Vec(N+1, new DecoupledMulti(new mb)))  // creating 1 extra to connect error device
    val devIn = Module(Flipped(Vec(N+1, new DecoupledMulti(new sb))))  // creating 1 extra to connect error device
    val devSel = IO(Input(UInt(log2Ceil(N + 1).W)))

  /** FIXME: assuming the socket is always ready to accept data from the bus host */
  hostIn.ready := true.B
  /** FIXME: assuming the socket is always ready to accept data from all the devices */
  devIn.map(b => b.ready := true.B)

  /** sending valid to error device only when host sends a valid req and the decoder cannot match
   * any address with the address map and sends a devSel for error device */
  devOut(N).valid := hostIn.valid && (devSel === N.asUInt)
  /** connecting the response with the error device by default
   * this would be overridden below if devSel matches with any devices */
  hostOut.valid := devIn(N).valid
  hostOut.bits <> devIn(N).bits

  /** connecting the bits from the host to all the devices
   * but connecting the valid to only that device which is selected by the decoder */
  devOut.map(dev => dev.bits <> hostIn.bits)
  for (i <- 0 until N) {
    devOut(i).valid := hostIn.valid && (devSel === i.asUInt)
  }

  /** if the devSel matches, then wire the host out with that device's signals
   * else, keep them connected with the error responder device. */
  for (id <- 0 until N) {
    when(devSel === id.asUInt) {
      hostOut.bits <> devIn(id).bits
      hostOut.valid := devIn(id).valid
    }
  }


}


object Switch1toNDriver extends App {
  implicit val config = WishboneConfig(addressWidth = 32, dataWidth = 32)
  println((new ChiselStage).emitVerilog(new Switch1toN[WBHost, WBDevice](new WishboneMaster(), new WishboneSlave(), 3)))
}
