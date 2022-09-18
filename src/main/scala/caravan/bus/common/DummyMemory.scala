package caravan.bus.common
import chisel3._
import chisel3.experimental.ChiselEnum
import chisel3.stage.ChiselStage
import chisel3.util.{Cat, Decoupled, MuxLookup}
import chisel3.util.experimental.loadMemoryFromFile


//implicit parameters for Config, Request and Response
class DummyMemController/*(programFile: Option[String])*/(implicit val config: BusConfig, implicit val request: AbstrRequest, implicit val response: AbstrResponse) extends MultiIOModule {
        val req = IO(Flipped(Decoupled(request)))
        val rsp = IO(Decoupled(response))

    val validReg = RegInit(false.B)
    // val ackWriteReg = RegInit(false.B)

    rsp.valid := validReg
    rsp.bits.error := false.B
    req.ready := true.B

    // masked memory init
    val mem = SyncReadMem(1024, Vec(4, UInt(8.W)))

    // if (programFile.isDefined) {
    //     loadMemoryFromFile(mem, programFile.get)
    // }

    // holds the data in byte vectors read from memory
    val rData = Wire(Vec(4,UInt(8.W)))
    // holds the bytes that must be read according to the activeByteLane
    val data = Wire(Vec(4,UInt(8.W)))

    when(req.fire() && req.bits.isWrite){

        
        mem.write(req.bits.addrRequest/4.U, req.bits.dataRequest.asTypeOf(Vec(4,UInt(8.W))), req.bits.activeByteLane.asBools)
        rData map (_ := DontCare)
        validReg := true.B
        // ackWriteReg := true.B


    }.elsewhen(req.fire() && !req.bits.isWrite){
         
        rData := mem.read(req.bits.addrRequest/4.U)
        validReg := true.B
        // ackWriteReg := false.B
        
    }.otherwise{
        
        rData map (_ := DontCare)
        validReg := false.B
        // ackWriteReg := false.B
        
    }

    data := req.bits.activeByteLane.asBools zip rData map {
        case (b:Bool, i:UInt) => Mux(b === true.B, i, 0.U)
    }

    rsp.valid := validReg
    rsp.bits.dataResponse := data.asUInt
    // rsp.bits.ackWrite := ackWriteReg

    

}