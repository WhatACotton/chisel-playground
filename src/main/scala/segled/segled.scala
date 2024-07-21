package segled

// SPDX-License-Identifier: BSL-1.0
// Copyright Kenta Ida 2021.
// Distributed under the Boost Software License, Version 1.0.
//    (See accompanying file LICENSE_1_0.txt or copy at
//          https://www.boost.org/LICENSE_1_0.txt)


import chisel3._
import chisel3.util._
import chisel3.experimental.ChiselEnum

// ShiftRegisterPort
// outputEnable: Bool 出力イネーブル
// shiftClock: Bool シフトクロック
// latch: Bool ラッチ
// data: Bool データ
class ShiftRegisterPort extends Bundle {
    val outputEnable = Output(Bool())
    val shiftClock = Output(Bool())
    val latch = Output(Bool())
    val data = Output(Bool())
}

// ShiftRegisterPortのコンパニオンオブジェクト
// apply: ShiftRegisterPortのインスタンスを生成
object ShiftRegisterPort {
    def apply() = new ShiftRegisterPort
}

// 引数
// numberOfSegments: Int 8セグメントLEDのセグメント数
// numberOfDigits: Int 8セグメントLEDの桁数
// shiftClockDivider: Int シフトクロックの分周数
// segmentUpdateDivider: Int セグメントの更新周期の分周数
// cathodeCommon: Boolean カソードコモンの場合はtrue、アノードコモンの場合はfalse
// isOEActiveLow: Boolean 出力イネーブルがアクティブローの場合はtrue、アクティブハイの場合はfalse
// アノードコモンとは、LEDのアノードが共通で、セグメントに対してカソードを接続する方式
// カソードコモンとは、LEDのカソードが共通で、セグメントに対してアノードを接続する方式
class SegmentLedWithShiftRegs(numberOfSegments: Int, numberOfDigits: Int, shiftClockDivider: Int, segmentUpdateDivider: Int, cathodeCommon: Boolean, isOEActiveLow: Boolean) extends Module {
    val io = IO(new Bundle {
        val segmentOut = ShiftRegisterPort()
        val digitSelector = ShiftRegisterPort()
        val digits = Input(Vec(numberOfDigits, UInt(numberOfSegments.W)))
    })

    val SEGMENT_UPDATE_COUNTER_BITS = log2Ceil(segmentUpdateDivider)
    val segmentUpdateCounter = RegInit(0.U(SEGMENT_UPDATE_COUNTER_BITS.W))
    val segmentLogic1 = cathodeCommon.B     // Segment output logic 1 level (cathode common: active high, anode common: active low)
    val digitLogic1 = (!cathodeCommon).B    // Digit selector output logic 1 level (cathode common: active low, anode common: active high)
    val outputEnableLogic1 = !isOEActiveLow.B   // Output enable output logic 1 level

    //７つのstateを持つ。
    object State extends ChiselEnum {
        val Reset, SetupDigit, ShiftDigit, SetupSegment, ShiftSegment, OutputDigit, HoldDigit = Value
    }
    //stateをResetに初期化
    val state = RegInit(State.Reset)

    //log2Ceil(numberOfSegments)のビット幅を持つのはなぜかというと、numberOfSegmentsが2のべき乗でない場合
    //numberOfSegmentsのビット幅を持つのではなく、numberOfSegmentsを超える最小の2のべき乗のビット幅を持つため
    val digitIndex = RegInit(0.U(log2Ceil(numberOfDigits).W))
    val segmentCounter = RegInit(0.U(log2Ceil(numberOfSegments).W))

    //log2Ceil(shiftClockDivider + 1)のビット幅を持つ
    //shiftClockCounterはシフトクロックの分周数待機する役割を持ち、SetupDigitsのステートで初期化し、
    //ShiftDigitのステートでデクリメントする
    val shiftClockCounter = RegInit(0.U(log2Ceil(shiftClockDivider + 1).W))
    val segmentShiftReg = RegInit(0.U(numberOfSegments.W))

    //初期化をしているが、以降はsegmentOutputEnableは引数のcathodeCommonの反転値を持つ
    //この４つはio.segmentOutの各信号に接続される
    val segmentOutputEnable = RegInit(!outputEnableLogic1)
    val segmentShiftClock = RegInit(false.B)
    val segmentLatch = RegInit(false.B)
    val segmentData = RegInit(!segmentLogic1)

    io.segmentOut.outputEnable := segmentOutputEnable
    io.segmentOut.shiftClock := segmentShiftClock
    io.segmentOut.latch := segmentLatch
    io.segmentOut.data := segmentData
    
    //この４つはio.digitSelectorの各信号に接続される
    val digitOutputEnable = RegInit(!outputEnableLogic1)
    val digitShiftClock = RegInit(false.B)
    val digitLatch = RegInit(false.B)
    val digitData = RegInit(!segmentLogic1)
    
    io.digitSelector.outputEnable := digitOutputEnable
    io.digitSelector.shiftClock := digitShiftClock
    io.digitSelector.latch := digitLatch
    io.digitSelector.data := digitData
    
    //state machine
    switch(state) {
        //Reset
        is(State.Reset) {
            //初期化
            digitIndex := 0.U
            segmentCounter := 0.U
            shiftClockCounter := 0.U
            segmentLatch := false.B
            digitLatch := false.B
            digitOutputEnable := !outputEnableLogic1
            segmentOutputEnable := !outputEnableLogic1
            state := State.SetupDigit
        }
        //SetupDigit
        is( State.SetupDigit ) {
            // output low to latch signals to output positive edge in OutputDigit state.
            digitLatch := false.B
            segmentLatch := false.B
            // digitIndexは入力から何番目の桁かを示す

            // Setup the digit selector output
            //digitIndexつまり最初の桁の場合は、digitLogic1をシフトインする。それ以外はレジスタをシフトするだけ
            digitData := Mux(digitIndex === 0.U, digitLogic1, !digitLogic1) // shift in logic 1 if this is the first digit. otherwise just shift the register.
            //ShiftDigitのstateではないので、digitShiftClockはfalse
            digitShiftClock := false.B
            //shiftClockDividerの値を引数からそのままshiftClockCounterにセット
            shiftClockCounter := shiftClockDivider.U
            //segmentShiftRegにio.digits(digitIndex)をセット。つまり、その桁のセグメントの情報をセット
            segmentShiftReg := io.digits(digitIndex)
            //segmentCounterを引数からそのままnumberOfSegments - 1にセット
            segmentCounter := (numberOfSegments - 1).U
            //stateをShiftDigitに変更
            state := State.ShiftDigit

            // update digit index to next digit
            // digitIndexに最後の桁の場合は0に、それ以外は+1する
            digitIndex := Mux(digitIndex === (numberOfDigits - 1).U, 0.U, digitIndex + 1.U)
        }
        //ShiftDigit
        is( State.ShiftDigit ) {
            // shiftClockCounterが0なら
            when( shiftClockCounter === 0.U ) {
                // digitShiftClockを反転
                digitShiftClock := !digitShiftClock
                //shiftClockDividerの値を引数からそのままshiftClockCounterにセット
                //つまり最初に来たときにshiftClockCounterがもう一度セットされる。
                shiftClockCounter := shiftClockDivider.U
                //digitShiftClockはこのループに来たタイミングだと、SetupDigitのときにfalseにしていたため、
                //内側には入らない。次に入るときに上のassignで反転されるため、この条件分岐はtrueになる
                when( digitShiftClock ) {
                    state := State.SetupSegment
                }
            } .otherwise {
                // shiftClockCounterが0でない場合は、shiftClockCounterをデクリメント
                shiftClockCounter := shiftClockCounter - 1.U
            }
        }
        //SetupSegment
        is( State.SetupSegment ) {
            // segmentShiftReg(numberOfSegments - 1)を出力の論理でセット
            segmentData := Mux(segmentShiftReg(numberOfSegments - 1), segmentLogic1, !segmentLogic1)
            // segmentShiftRegを1ビット左シフトして、最下位ビットに0をセット
            segmentShiftReg := Cat(segmentShiftReg(numberOfSegments - 2, 0), 0.U(1.W))
            // segmentShiftClockをfalseにセット
            segmentShiftClock := false.B
            // shiftClockDividerの値をカウントアップ
            shiftClockCounter := shiftClockDivider.U
            // ShiftSegmentに遷移
            state := State.ShiftSegment
        }
        //ShiftSegment
        is( State.ShiftSegment ) {
            // shiftClockCounterが0なら
            when( shiftClockCounter === 0.U ) {
                segmentShiftClock := !segmentShiftClock
                //shiftClockCounterをカウントアップ
                shiftClockCounter := shiftClockDivider.U
                when( segmentShiftClock ) {
                    //segmentCounterが0になるまで、segmentCounterをデクリメントしてSetupSegmentに遷移
                    when( segmentCounter === 0.U ) {
                        state := State.OutputDigit
                    } .otherwise {
                        segmentCounter := segmentCounter - 1.U
                        state := State.SetupSegment
                    }
                }
            } .otherwise {
                shiftClockCounter := shiftClockCounter - 1.U
            }
        }
        //OutputDigit
        is( State.OutputDigit ) {
            // Latches both the digit selector and segment output and then enable these outputs.
            digitLatch := true.B
            segmentLatch := true.B
            digitOutputEnable := outputEnableLogic1
            segmentOutputEnable := outputEnableLogic1
            //segmentUpdateCounterをセット
            segmentUpdateCounter := segmentUpdateDivider.U
            state := State.HoldDigit
        }
        //HoldDigit
        is( State.HoldDigit ) {
            // Wait until the segment update counter reaches 0.
            //segmentUpdateCounterが0になるまでデクリメント
            when ( segmentUpdateCounter === 0.U ) {
                state := State.SetupDigit
            } .otherwise {
                segmentUpdateCounter := segmentUpdateCounter - 1.U
            }
        }
    }
}