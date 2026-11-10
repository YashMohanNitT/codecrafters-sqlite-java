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
          System.err.println((((int)fileBytes[16])<<8) + " " + fileBytes[17]);

          // "& 0xFF" turns a signed Java byte (-128..127) into an unsigned value (0..255).
          int pageSize = (fileBytes[16] & 0xFF) << 8 | (fileBytes[17] & 0xFF);

          // The file header is 100 bytes. The page header of page 1 starts right after it.
          // The cell count is the 2-byte big-endian number at bytes 3 and 4 of the page header,
          // so it is at file offsets 103 and 104.
          // Each cell is one row of sqlite_schema, and each row is one table.
          int pageHeaderStart = 100;
          int cellCount = (fileBytes[pageHeaderStart + 3] & 0xFF) << 8
              | (fileBytes[pageHeaderStart + 4] & 0xFF);

          // You can use print statements as follows for debugging, they'll be visible when running tests.
          System.err.println("Logs from your program will appear here!");

          System.out.println("database page size: " + pageSize);
          System.out.println("number of tables: " + cellCount);
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
