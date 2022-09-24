package caravan.bus.tilelink
import caravan.bus.common.HostAdapter
import chisel3._
import chisel3.experimental.DataMirror
import chisel3.stage.ChiselStage
import chisel3.util._

class TilelinkHost(implicit val config: TilelinkConfig) extends HostAdapter with OpCodes {
        val tlMasterTransmitter = IO(DecoupledMulti(new TilelinkMaster()))
        val tlSlaveReceiver  = IO(Flipped(DecoupledMulti(new TilelinkSlave())))
        val reqIn = IO(Flipped(DecoupledMulti(new TLRequest())))
        val rspOut = IO(DecoupledMulti(new TLResponse()))


    //FSM for indicating valid response only when the response comes.
    val idle :: wait_for_resp :: Nil = Enum(2)
    val stateReg = RegInit(idle)
    val addrReg  = RegInit(0.U)
    // val respReg = RegInit(false.B)
    // val readyReg = RegInit(true.B)
    dontTouch(stateReg)
    dontTouch(reqIn.valid)
    // when(fire) {
    //     readyReg := false.B
    // }
    // when(stateReg === latch_data) {
    //     readyReg := true.B
    // }

    tlSlaveReceiver.ready    := false.B
    reqIn.ready              := true.B


    // rspOut.bits.dataResponse := tlSlaveReceiver.bits.d_data  
    // rspOut.bits.error        := tlSlaveReceiver.bits.d_denied
    // rspOut.bits.ackWrite     := tlSlaveReceiver.bits.d_opcode === AccessAckData.U

    tlMasterTransmitter.bits.a_opcode    := 0.U
    tlMasterTransmitter.bits.a_data      := 0.U
    tlMasterTransmitter.bits.a_address   := addrReg
    tlMasterTransmitter.bits.a_param     := 0.U
    tlMasterTransmitter.bits.a_source    := 0.U
    tlMasterTransmitter.bits.a_size      := 0.U
    tlMasterTransmitter.bits.a_mask      := 0.U
    tlMasterTransmitter.bits.a_corrupt   := 0.U
    tlMasterTransmitter.valid            := 0.U

    rspOut.bits.dataResponse             := 0.U  
    rspOut.bits.error                    := 0.U
    // rspOut.bits.ackWrite                 := 0.U
    rspOut.valid                         := false.B


    when(stateReg === idle){
        // stateReg := Mux(reqIn.valid, process_data, idle)
    // }.elsewhen(stateReg === process_data){

        when(reqIn.valid){

            tlMasterTransmitter.bits.a_opcode    := Mux(reqIn.bits.isWrite, Mux(reqIn.bits.activeByteLane === "b1111".U, PutFullData.U, PutPartialData.U) , Get.U)/*, 2.U)*/
            tlMasterTransmitter.bits.a_data      := reqIn.bits.dataRequest
            tlMasterTransmitter.bits.a_address   := reqIn.bits.addrRequest
            tlMasterTransmitter.bits.a_param     := 0.U
            tlMasterTransmitter.bits.a_source    := 2.U 
            tlMasterTransmitter.bits.a_size      := MuxLookup(config.w.U, 2.U,Array(                    // default 32-bit
                                                                                    (1.U) -> 0.U,
                                                                                    (2.U) -> 1.U,
                                                                                    (4.U) -> 2.U,
                                                                                    (8.U) -> 3.U
                                                                                ))
            tlMasterTransmitter.bits.a_mask      := reqIn.bits.activeByteLane
            tlMasterTransmitter.bits.a_corrupt   := false.B
            tlMasterTransmitter.valid            := reqIn.valid

            stateReg := wait_for_resp
            tlSlaveReceiver.ready := true.B 
            addrReg := reqIn.bits.addrRequest

        }

        
    }.elsewhen(stateReg === wait_for_resp){

        tlSlaveReceiver.ready := true.B
        reqIn.ready           := false.B

        when(tlSlaveReceiver.valid){

            rspOut.bits.dataResponse := tlSlaveReceiver.bits.d_data  
            rspOut.bits.error := tlSlaveReceiver.bits.d_denied
            // rspOut.bits.ackWrite := tlSlaveReceiver.bits.d_opcode === AccessAckData.U
            rspOut.valid := tlSlaveReceiver.valid
            stateReg := idle

        }

    }

    // tlSlaveReceiver.ready := true.B
    // reqIn.ready := true.B


    // when(reqIn.valid){
        // tlMasterTransmitter.bits.a_opcode := /*Mux(readyReg,*/ Mux(reqIn.bits.isWrite, Mux(reqIn.bits.activeByteLane === "b1111".U, PutFullData.U, PutPartialData.U) , Get.U)/*, 2.U)*/
        // tlMasterTransmitter.bits.a_data := reqIn.bits.dataRequest
        // tlMasterTransmitter.bits.a_address := reqIn.bits.addrRequest
        // tlMasterTransmitter.bits.a_param := 0.U
        // tlMasterTransmitter.bits.a_source := 2.U 
        // tlMasterTransmitter.bits.a_size := MuxLookup(config.w.U, 2.U,Array(                    // default 32-bit
        //                                                                         (1.U) -> 0.U,
        //                                                                         (2.U) -> 1.U,
        //                                                                         (4.U) -> 2.U,
        //                                                                         (8.U) -> 3.U
        //                                                                     ))
        // tlMasterTransmitter.bits.a_mask := reqIn.bits.activeByteLane
        // tlMasterTransmitter.bits.a_corrupt := false.B
        // tlMasterTransmitter.valid := reqIn.valid

    // } otherwise {
    //     tlMasterTransmitter.bits.a_opcode := 2.U         // 2 is used for DontCare
    //     tlMasterTransmitter.bits.a_data := DontCare
    //     tlMasterTransmitter.bits.a_address := DontCare
    //     tlMasterTransmitter.bits.a_param := DontCare
    //     tlMasterTransmitter.bits.a_source := DontCare
    //     tlMasterTransmitter.bits.a_size := DontCare
    //     tlMasterTransmitter.bits.a_mask := DontCare
    //     tlMasterTransmitter.bits.a_corrupt := DontCare
    //     tlMasterTransmitter.valid := false.B
    // }

    // response is valid when either acknowledment or error is coming back.
    // respReg := MuxCase(false.B,Array(
    //     ((tlSlaveReceiver.bits.d_opcode === AccessAck.U || tlSlaveReceiver.bits.d_opcode === AccessAckData.U) && !tlSlaveReceiver.bits.d_denied) -> true.B,
    //     (tlSlaveReceiver.bits.d_denied & tlSlaveReceiver.valid) -> true.B,
    // ))
    

    // when(stateReg === idle){
    //     stateReg := Mux(
    //         (tlSlaveReceiver.bits.d_denied |
    //         (tlSlaveReceiver.bits.d_opcode === AccessAck.U || tlSlaveReceiver.bits.d_opcode === AccessAckData.U)),
    //         latch_data,
    //         idle
    //     )
    // }.elsewhen(stateReg === latch_data){
    //     respReg := false.B                  // response is invalid for idle state
    //     stateReg := idle
    // }
    


    // only valid resp is Reg'ed because data and error are coming from device after being stalled already.
   


}