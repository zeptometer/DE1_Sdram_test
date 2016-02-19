import Chisel._
import aqua.uart._
import aqua.sdram._
import aqua.dummysdram._

class SdramTest (
  val validWidth: Int
) extends Module {

  val io = new Bundle {
    val uart  = new UartIO
    val sdram = new SdramIO
  }

  val writing :: reading :: Nil = Enum(UInt(), 2)
  val state = RegInit(writing)
  val state_counter = RegInit(UInt(0, width = 23)) // 22 + 1
  val burst_counter = RegInit(UInt(0, width = 4))  //  3 + 1

  printf("current state_counter: %x\n", state_counter)

  // registers for write_state
  val write_idle :: write_writing :: Nil = Enum(UInt(), 2)
  val write_state = RegInit(write_idle)

  // registers for read_state
  val read_idle :: read_reading :: read_emit :: Nil = Enum(UInt(), 3)
  val read_state = RegInit(read_idle)
  val readbuf = RegInit(Vec.fill(8) {UInt(0, width = 16)})

  // default values
  io.uart.enq.valid := Bool(false)
  io.uart.enq.bits  := UInt(0)
  io.uart.deq.ready := Bool(false)

  io.sdram.wdata.valid   := Bool(false)
  io.sdram.wdata.bits    := state_counter(15, 0)
  io.sdram.cmd.valid     := Bool(false)
  io.sdram.cmd.bits.we   := Bool(true)
  io.sdram.cmd.bits.addr := UInt(0)

  // logic
  when (state_counter >= UInt(1 << (validWidth - 3))
        && ((state === writing && write_state === write_idle)
            || (state === reading && read_state === read_idle))) {
    switch (state) {
      is (writing) {state := reading}
      is (reading) {state := writing}
    }

    state_counter := UInt(0)
    burst_counter := UInt(0)
    write_state   := write_idle
    read_state    := read_idle

  } .otherwise {
    switch (state) {
      is (writing) {
        switch (write_state) {
          is (write_idle) {
            printf("state: write_idle\n")
            when (io.sdram.cmd.ready) {
	      write_state            := write_writing
              burst_counter          := UInt(0)
              io.sdram.cmd.valid     := Bool(true)
              io.sdram.cmd.bits.we   := Bool(true)
              io.sdram.cmd.bits.addr := Cat(state_counter(21, 0),
                                            state_counter(5, 3))
            }
          }

          is (write_writing) {
            printf("state: write_writing\n")
            io.sdram.wdata.valid := Bool(true)
            io.sdram.wdata.bits  := Cat(state_counter(21, 0),
                                        state_counter(5, 3) ^ burst_counter(2, 0))
            burst_counter        := burst_counter + UInt(1)

            when (burst_counter === UInt(7)) {
              write_state := write_idle
              state_counter := state_counter + UInt(1)
            }
          }
        }
      }

      is (reading) {
        switch (read_state) {
          is (read_idle) {
            printf("state: read_idle\n")
            when (io.sdram.cmd.ready) {
              read_state             := read_reading
              burst_counter          := UInt(0)
              io.sdram.cmd.valid     := Bool(true)
              io.sdram.cmd.bits.we   := Bool(false)
              io.sdram.cmd.bits.addr := Cat(state_counter(21, 0), UInt(0, width = 3))
            }
          }

          is (read_reading) {
            printf("state: read_reading@burst = %d\n", burst_counter)
            when (burst_counter >= UInt(8)) {
              read_state    := read_emit
              burst_counter := UInt(0)

            } .elsewhen (io.sdram.rdata.valid) {
              burst_counter := burst_counter + UInt(1)
              readbuf(burst_counter) := io.sdram.rdata.bits
              printf("readbuf(%d) <- %d\n", burst_counter, io.sdram.rdata.bits)
            }
          }

          is (read_emit) {
            printf("state: read_emit@burst = %d\n", burst_counter)
            when (burst_counter >= UInt(8)) {
              read_state    := read_idle
              state_counter := state_counter + UInt(1)
              burst_counter := UInt(0)

            } .elsewhen (io.uart.enq.ready) {
              printf("readbuf(%d) -> %d\n", burst_counter, readbuf(burst_counter))
              io.uart.enq.valid := Bool(true)
              io.uart.enq.bits  := readbuf(burst_counter)(7, 0) ^ readbuf(burst_counter)(15, 8)
              burst_counter     := burst_counter + UInt(1)
            }
          }
        }
      }
    }
  }
}

class ActualSdramTest() extends Module {
  val io = new Bundle {
    val sdram = new SdramPeripheral
    val uart  = new UartPeripheral
  }

  val sdram = Module(new Sdram(
    frequency = 143,
    casLatency = 3,
    burstLength = 8,
    interleaved = true,
    tPowerUp = 100,
    tREF = 64,
    tRCD = 15,
    tRAS = 37,
    tDPL = 14,
    tRP  = 15,
    tRC  = 60,
    tMRD = 14
  ))
  val uart = Module(new BufferedUart(0x1adb, 1024))
  val test = Module(new SdramTest(validWidth = 22))

  test.io.uart  <> uart.io.ctl
  test.io.sdram <> sdram.io.ctl
  io.uart  <> uart.io.pins
  io.sdram <> sdram.io.pins
}

class DummySdramTest(val validWidth: Int = 10) extends Module {
  val io = Valid(UInt(width = 8))

  val sdram = Module(new DummySdram(validWidth = validWidth,
                                    burstLength = 8,
                                    casLatency = 3))
  val test = Module(new SdramTest(validWidth = validWidth))

  test.io.sdram <> sdram.io

  test.io.uart.deq.valid := Bool(false)
  test.io.uart.deq.bits  := UInt(0)
  test.io.uart.enq.ready := Bool(true)
  io.valid := test.io.uart.enq.valid
  io.bits  := test.io.uart.enq.bits
}

object SdramTest {

  def main(args: Array[String]): Unit = {
    chiselMainTest(args, () => Module(new ActualSdramTest)) { c =>
      new ActualSdramTestTest(c)
    }

    chiselMainTest(args, () => Module(new DummySdramTest(16))) { c =>
      new DummySdramTestTest(c)
    }
  }

  class ActualSdramTestTest(c: ActualSdramTest) extends Tester(c, isTrace = false) {
    poke(c.io.sdram.dqi, 1)
  }

  class DummySdramTestTest(c: DummySdramTest) extends Tester(c, isTrace = false) {
    for (i <- 0 until 1 << (c.validWidth + 1)) {
      while (peek(c.io.valid) == 0) {
        step(1)
      }
      expect(c.io.bits, (i & 255) ^ ((i >> 8) & 255))
      step(1)
    }
  }
}
