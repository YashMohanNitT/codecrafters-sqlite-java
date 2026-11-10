import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Main {
  public static void main(String[] args){
    if (args.length < 2) {
      System.out.println("Missing <database path> and <command>");
      return;
    }

    String databaseFilePath = args[0];
    String command = args[1];

    switch (command) {
      case ".dbinfo" -> {
        try {
          // Read the whole file, so we can print every byte of it.
          byte[] fileBytes = Files.readAllBytes(Path.of(databaseFilePath));

          // The page size is the 2 bytes at offset 16 of the header.
          // 00010000 000000000 -> 16 0 in big endian
          // 00000000 000001000 -> 0 16 in little endian
          System.out.println((((int)fileBytes[16])<<8) + " " + fileBytes[17]);

          int pageSize = fileBytes[16]<<8 | fileBytes[17];

          // You can use print statements as follows for debugging, they'll be visible when running tests.
          System.err.println("Logs from your program will appear here!");

          // TODO: Uncomment the code below to pass the first stage
           System.out.println("database page size: " + pageSize);
        } catch (IOException e) {
          System.out.println("Error reading file: " + e.getMessage());
        }
      }
      default -> System.out.println("Missing or invalid command passed: " + command);
    }
  }

  // Writes the raw bytes of the file to stderr, unchanged. stdout stays clean.
  private static void printFile(byte[] bytes) {
    System.out.println(Arrays.toString(bytes));
  }
}
