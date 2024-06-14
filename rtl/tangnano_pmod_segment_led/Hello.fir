circuit Hello :
  module Hello :
    input clock : Clock
    input reset : UInt<1>
    output io : { led : UInt<1>}

    reg cntReg : UInt<32>, clock with :
      reset => (reset, UInt<32>("h0")) @[main.scala 9:23]
    reg blkReg : UInt<1>, clock with :
      reset => (reset, UInt<1>("h0")) @[main.scala 10:23]
    node _cntReg_T = add(cntReg, UInt<1>("h1")) @[main.scala 12:20]
    node _cntReg_T_1 = tail(_cntReg_T, 1) @[main.scala 12:20]
    cntReg <= _cntReg_T_1 @[main.scala 12:10]
    node _T = eq(cntReg, UInt<25>("h17d783f")) @[main.scala 13:15]
    when _T : @[main.scala 13:28]
      cntReg <= UInt<1>("h0") @[main.scala 14:12]
      node _blkReg_T = not(blkReg) @[main.scala 15:15]
      blkReg <= _blkReg_T @[main.scala 15:12]
    io.led <= blkReg @[main.scala 17:10]

