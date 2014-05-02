package ru.ifmo.ctddev.shalamov.hw1;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.InputMismatchException;
import java.util.Scanner;


public class Grep {
    public static void main(String[] args) {
        final Path startPath = Paths.get(System.getProperty("user.dir"));

        final String[] strings;

        if (args.length == 0) {
            System.out.println("No Arguments.");
            System.exit(0);
        }
        if (!"-".equals(args[0])) {
            strings = args;
        } else {
            int n;
            Scanner in = new Scanner(System.in, "UTF-8");
            try {
                n = in.nextInt();
            } catch (InputMismatchException imexc) {
                System.out.println("No idea");
                n = 0;
            }

            if (n <= 0) System.exit(0);

            String[] arr = new String[n];


            for (int i = 0; i < n; ++i) {
                BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
                String line = null;
                try {
                    line = br.readLine();
                } catch (IOException ioexc) {
                    System.out.println("IO error");
                    System.exit(0);
                }
                arr[i] = line;
            }
            strings = arr;
        }

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                    String line;
                    BufferedReader in = Files.newBufferedReader(file, Charset.defaultCharset());

                    try {
                        while ((line = in.readLine()) != null) {
                            for (String s : strings) {
                                if (line.lastIndexOf(s) != -1) {
                                    System.out.println(startPath.relativize(file) + " :  " + line);
                                    break;
                                }
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    } catch (IOException ioexc) {
                        return FileVisitResult.CONTINUE;
                    }
                }
            });
        } catch (IOException ioexc) {
            System.out.println("IO error");
            System.exit(0);
        }
    }
}
