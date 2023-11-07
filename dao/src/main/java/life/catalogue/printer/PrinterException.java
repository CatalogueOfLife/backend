package life.catalogue.printer;

public class PrinterException extends RuntimeException {

  public PrinterException() {
  }

  public PrinterException(Throwable cause) {
    super(cause);
  }

  public PrinterException(String message, Throwable cause) {
    super(message, cause);
  }
}
