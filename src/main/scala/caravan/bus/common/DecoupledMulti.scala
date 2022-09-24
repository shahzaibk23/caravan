package caravan.bus.common
import chisel3._

class DecoupledMulti[T <: Data](gen: T) extends MultiIOModule {
    val ready = IO(Input(Bool()))
    val valid = IO(Output(Bool()))
    val bits = IO(Output(gen))
}