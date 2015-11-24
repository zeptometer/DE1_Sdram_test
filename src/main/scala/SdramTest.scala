
import Chisel._

class SdramTest (
  val validWidth: Int
) extends Module {

  val io = new Bundle {
    val uart  = new UartIO
    val sdram = new SdramIO
  }

  val writing :: reading :: Nil = Enum(UInt(), 2)
  val state = RegInit(writing)
  val state_counter = RegInit(UInt(0, width = 32))
  val burst_counter = RegInit(UInt(0, width = 5))

  // registers for write_state
  val write_idle :: write_writing :: Nil = Enum(UInt(), 2)
  val write_state = RegInit(write_idle)

  // registers for read_state
  val read_idle :: read_reading :: read_emit :: Nil = Enum(UInt(), 3)
  val read_state = RegInit(read_idle)
  val readbuf = Vec.fill(8) {UInt(0, width = 16)}

  // default values
  io.uart.enq.valid := Bool(false)
  io.uart.enq.bits  := UInt(0)
  io.uart.deq.ready := Bool(false)

  io.sdram.wdata.valid   := Bool(false)
  io.sdram.wdata.bits    := state_counter(15, 0)
  io.sdram.cmd.valid     := Bool(false)
  io.sdram.cmd.bits.we   := Bool(true)
  io.sdram.cmd.bits.addr := state_counter(25, 4)

  // logic
  when (state_counter === UInt(1 << validwidth)) {
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
            when (io.sdram.cmd.ready) {
	      write_state          := write_writing
              burst_counter        := UInt(0)
              io.sdram.cmd.valid   := Bool(true)
              io.sdram.cmd.bits.we := Bool(true)
            }
          }

          is (write_writing) {
            io.sdram.wdata.valid := Bool(true)
            state_counter        := state_counter + UInt(1)
            burst_counter        := burst_counter + UInt(1)

            when (burst_counter === UInt(7)) {
              write_state := write_idle
            }
          }
        }
      }

      is (reading) {
        switch (read_state) {
          is (read_idle) {
            when (io.sdram.cmd.ready) {
              read_state           := read_reading
              burst_counter        := UInt(0)
              io.sdram.cmd.valid   := Bool(true)
              io.sdram.cmd.bits.we := Bool(false)
            }
          }

          is (read_reading) {
            when (burst_counter === UInt(8)) {
              write_state   := read_emit
              burst_counter := UInt(0)

            } .elsewhen (io.sdram.rdata.valid) {
              state_counter := state_counter + UInt(1)
              burst_counter := burst_counter + UInt(1)
              readbuf(burst_counter) := io.sdram.rdata.bits
            }
          }

          is (read_emit) {
            when (burst_counter === UInt(8)) {
              read_state    := read_idle
              burst_counter := UInt(0)

            } .elsewhen (io.uart.enq.ready) {
              io.uart.enq.valid := Bool(true)
              io.uart.enq.bits  := readbuf(burst_counter)
              burst_counter        := burst_counter + UInt(1);
            }
          }
        }
      }
    }
  }
}

class ActualSdramTest() extends Module {

  val io = new Bundle {
    val sdramRaw = new SdramRawIO
    val uartRaw  = new UartRawIO
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

  test.io.uart     <> uart.io.ctl
  test.io.sdram    <> sdram.io.ctl
  test.io.uartRaw  <> uart.io.raw
  test.io.sdramRaw <> sdram.io.raw
}

class DummySdramTest() extends Module {

  val io = new Bundle()

  val sdram = Module(new DummySdram(validWidth = 16,
                                    burstLength = 16,
                                    casLatency = 3))
  val uart = Module(new DummyUart())
  val test = Module(new SdramTest(validWidth = 16))

  test.io.uart  <> uart.io
  test.io.sdram <> sdram.io
}

object SdramTest {

  def main(args: Array[String]): Unit = {
    chiselMainTest(args, () => Module(new SdramTest)) { c =>
      new SdramTestTest(c)
    }
  }

  class SdramTestTest(c: SdramTest) extends Tester(c, isTrace = false) {
    poke(c.io.sdram.dqi, 1)
  }
}
