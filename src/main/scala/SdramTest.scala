
import Chisel._

class SdramTest (
) extends Module {

  val io = new Bundle {
    val uart = new Bundle {
      val rx = Bool(INPUT)
      val tx = Bool(OUTPUT)
    }
    val sdram = new Bundle {
      val a    = Bits(OUTPUT, 13)
      val ba   = Bits(OUTPUT, 2)
      val xcas = Bool(OUTPUT)
      val cke  = Bool(OUTPUT)
      val xcs  = Bool(OUTPUT)
      val dqml = Bool(OUTPUT)
      val dqmh = Bool(OUTPUT)
      val dqi  = Bits(INPUT, 16)
      val dqo  = Bits(OUTPUT, 16)
      val xras = Bool(OUTPUT)
      val xwe  = Bool(OUTPUT)
    };
  }

  val uartCtl = BufferedUart(
    wtime = 0x1ADB,
    entries = 1024)

  io.uart.tx    := uartCtl.io.tx
  uartCtl.io.rx := io.uart.rx

  val sdramCtl = Module(new SdramController(
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

  io.sdram <> sdramCtl.io.sdram

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
  uartCtl.enq.valid := Bool(false)
  uartCtl.enq.bits  := UInt(0)
  uartCtl.deq.ready := Bool(false)

  sdramCtl.wdata.valid   := Bool(false)
  sdramCtl.wdata.bits    := state_counter(15, 0)
  sdramCtl.rdata.ready   := Bool(false)
  sdramCtl.cmd.valid     := Bool(false)
  sdramCtl.cmd.bits.we   := Bool(true)
  sdramCtl.cmd.bits.addr := state_counter(25, 4)

  // logic
  when (state_counter === UInt(1 << 22)) {
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
            when (sdramCtl.io.cmd.ready) {
	      write_state          := write_writing
              burst_counter        := UInt(0)
              sdramCtl.cmd.valid   := Bool(true)
              sdramCtl.cmd.bits.we := Bool(true)
            }
          }

          is (write_writing) {
            sdramCtl.wdata.valid := Bool(true)
            state_counter        := state_counter + UInt(1)
            burst_counter        := burst_counter + UInt(1)

            when (write_counter === 7) {
              write_state := write_idle
            }
          }
        }
      }

      is (reading) {
        switch (read_state) {
          is (read_idle) {
            when (sdramCtl.io.cmd.ready) {
              read_state           := read_reading
              burst_counter        := UInt(0)
              sdramCtl.cmd.valid   := Bool(true)
              sdramCtl.cmd.bits.we := Bool(false)
            }
          }

          is (read_reading) {
            sdramCtl.io.rdata.ready := Bool(true)

            when (write_counter === 8) {
              write_state   := read_emit
              burst_counter := UInt(0)
            } .elsewhen (sdramCtl.io.rdata.valid) {
              state_counter := state_counter + UInt(1)
              burst_counter := burst_counetr + UInt(1)
              readbuf(burst_counter) := sdramCtl.io.rdata.bits
            }
          }

          is (read_emit) {
            when (burst_couneter === UInt(8)) {
              read_state    := read_idle
              burst_counter := UInt(0)

            } .elsewhen (uartCtl.io.enq.ready) {
              uartCtl.io.enq.valid := Bool(true)
              uartCtl.io.enq.bits  := readbuf(burst_counter)
              burst_couneter       := burst_counter + UInt(1);
            }
          }
        }
      }
    }
  }
}
