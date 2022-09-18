package caravan.bus.tilelink
import caravan.bus.common.DeviceAdapter
import chisel3._
import chisel3.stage.ChiselStage
import chisel3.util._

class TilelinkCDevice(implicit val config: TilelinkConfig) extends DeviceAdapter with OpCodes {
        val tlSlaveTransmitter = IO(Decoupled(new TilelinkSlave()))
        val tlMasterReceiver = IO(Flipped(Decoupled(new TilelinkMaster())))
        val reqOut = IO(Decoupled(new TLRequest()))
        val rspIn = IO(Flipped(Decoupled(new TLResponse())))

        // val tlAckReceiver = Flipped(Decoupled(new channelEBundle))


    val idle :: wait_for_resp :: Nil = Enum(2)
    val stateReg = RegInit(idle)

    tlMasterReceiver.ready := true.B
    rspIn.ready := false.B


    reqOut.bits.addrRequest      := 0.U
    reqOut.bits.dataRequest      := 0.U
    reqOut.bits.activeByteLane   := 0.U
    reqOut.bits.isWrite          := 0.U
    reqOut.valid                 := 0.U

    tlSlaveTransmitter.bits.d_opcode     := 0.U
    tlSlaveTransmitter.bits.d_data       := 0.U
    tlSlaveTransmitter.bits.d_param      := 0.U
    tlSlaveTransmitter.bits.d_size       := 0.U
    tlSlaveTransmitter.bits.d_source     := 0.U
    tlSlaveTransmitter.bits.d_sink       := 0.U
    tlSlaveTransmitter.bits.d_denied     := 0.U     // d_denied pin is used for representing Mem error
    tlSlaveTransmitter.bits.d_corrupt    := 0.U
    tlSlaveTransmitter.valid             := 0.U
    
    // val stall = Module(new stallUnit)

    when(stateReg === idle){

        when(tlMasterReceiver.valid){

            reqOut.bits.addrRequest := tlMasterReceiver.bits.a_address
            reqOut.bits.dataRequest := tlMasterReceiver.bits.a_data
            reqOut.bits.activeByteLane := tlMasterReceiver.bits.a_mask
            reqOut.bits.isWrite := tlMasterReceiver.bits.a_opcode === PutFullData.U || tlMasterReceiver.bits.a_opcode === PutPartialData.U
            reqOut.valid := true.B

            stateReg := wait_for_resp
            rspIn.ready := true.B

        }

    }.elsewhen(stateReg === wait_for_resp){

        rspIn.ready := true.B

        when(rspIn.valid){

            tlSlaveTransmitter.bits.d_opcode := AccessAckData.U
            tlSlaveTransmitter.bits.d_data := rspIn.bits.dataResponse
            tlSlaveTransmitter.bits.d_param := 0.U
            tlSlaveTransmitter.bits.d_size := tlMasterReceiver.bits.a_size
            tlSlaveTransmitter.bits.d_source := tlMasterReceiver.bits.a_source
            tlSlaveTransmitter.bits.d_sink := 0.U
            tlSlaveTransmitter.bits.d_denied := rspIn.bits.error      // d_denied pin is used for representing Mem error
            tlSlaveTransmitter.bits.d_corrupt := 0.U
            tlSlaveTransmitter.valid := rspIn.valid

            stateReg := idle

        }

    }


    // Sending Response coming from Memory in the STALL to delay the response one cycle
   

    
}