package caravan.bus.tilelink

import chisel3._ 

// Module to Stall the Response one Cycle

class stallUnit extends MultiIOModule {

    implicit val config = TilelinkConfig()

        val bundle_in = IO(Input(new TilelinkSlave))
        val valid_in = IO(Input(UInt(1.W)))
        val bundle_out = IO(Output(new TilelinkSlave))
        val valid_out = IO(Output(UInt(1.W)))

    

    val bundle_reg = WireInit(0.U.asTypeOf(new TilelinkSlave))
    val valid_reg = WireInit(0.U(1.W))
    
    bundle_reg := bundle_in
    valid_reg := valid_in

    bundle_out := bundle_reg
    valid_out := valid_reg
}