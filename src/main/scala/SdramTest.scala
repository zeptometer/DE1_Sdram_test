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

  val uart = BufferedUart(
    wtime = 0x1db,
    entries = 1024)

  io.uart.tx := uart.io.tx
  uart.io.rx := io.uart.rx

  val sdramController = Module(new SdramController(
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

  io.sdram <> sdramController.io.sdram

  // fixme
}
