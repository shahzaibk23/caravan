package caravan.bus.tilelink

import chisel3._ 
import chisel3.util._

class TilelinkAdapter(implicit val config:TilelinkConfig) extends MultiIOModule {

        /*  MASTER SIDE  */
        val reqIn =  IO(Flipped(DecoupledMulti(new TLRequest)))
        val rspOut = IO(DecoupledMulti(new TLResponse))

        /*  SLAVE SIDE */
        val reqOut = IO(DecoupledMulti(new TLRequest))
        val rspIn = IO(Flipped(DecoupledMulti(new TLResponse))
)
    val tlHost = Module(new TilelinkHost)
    val tlSlave = Module(new TilelinkDevice)

    /*  Connecting Master Interconnects  */
    tlHost.tlMasterTransmitter <> tlSlave.tlMasterReceiver

    /*  Connecting Slave Interconnects  */
    tlSlave.tlSlaveTransmitter <> tlHost.tlSlaveReceiver

    /*  Sending Request in Master  */
    tlHost.reqIn <> reqIn

    /*  Sending Response out from Master  */
    rspOut <> tlHost.rspOut

    /*  Sending Request out from Slave  */
    reqOut <> tlSlave.reqOut

    /*  Sending Response in Slave  */
    tlSlave.rspIn <> rspIn
}