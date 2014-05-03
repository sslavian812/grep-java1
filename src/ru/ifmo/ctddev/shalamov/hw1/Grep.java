package ru.ifmo.ctddev.shalamov.hw1;


import javafx.util.Pair;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.Scanner;


public class Grep {
    private static final String UTF_8 = "UTF-8";
    private static final String CP1251 = "CP1251";
    private static final String CP866 = "CP866";
    private static final String KOI8_R = "KOI8-R";

    private static final String FROM_CONSOLE = "-";

    private static final String NO_ARGS = "No Arguments.";
    private static final String TOO_MUCH = "too mach args";
    private static final String START_POINT = "user.dir";
    private static final String NO_IDEA_WHY = "No idea";
    private static final String IO_ERROR = "IO error";


    private static final int MAX_PATTERNS_COUNT = 1000;
    private static int MAX_PATTERN_LENGTH = -1;
    private static int MY_BUFFER_SIZE = 8 * 1024;

    private static String sep = System.lineSeparator();
    private static int sepsize = sep.length();

    private static ArrayList<Pair<byte[], String>> patterns;


    public static void main(String[] args) {
        final Path startPath = Paths.get(System.getProperty(START_POINT));

        patterns = new ArrayList<Pair<byte[], String>>();

        if (args.length == 0) {
            System.out.println(NO_ARGS);
            System.exit(1);
        }

        if (args.length > MAX_PATTERNS_COUNT) {
            System.out.println(TOO_MUCH);
            System.exit(1);
        }


        if (!FROM_CONSOLE.equals(args[0])) {       // command line args
            for (String arg : args) {

                byte[] bytes;
                bytes = arg.getBytes(Charset.forName(UTF_8));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, UTF_8));

                bytes = arg.getBytes(Charset.forName(CP866));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, CP866));

                bytes = arg.getBytes(Charset.forName(CP1251));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, CP1251));

                bytes = arg.getBytes(Charset.forName(KOI8_R));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, KOI8_R));
            }
        } else {                            // retrieve from user
            int n;
            Scanner in = new Scanner(System.in, UTF_8);
            try {
                n = in.nextInt();
            } catch (InputMismatchException imexc) {
                System.out.println(NO_IDEA_WHY);
                n = 0;
            }

            if (n <= 0) System.exit(1);


            for (int i = 0; i < n; ++i) {
                String line = null;
                try {
                    BufferedReader br = new BufferedReader(new InputStreamReader(System.in, UTF_8));
                    line = br.readLine();
                } catch (IOException ioexc) {
                    System.out.println(IO_ERROR);
                    System.exit(1);
                }

                if (line == null)
                    continue;

                byte[] bytes;
                bytes = line.getBytes(Charset.forName(UTF_8));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, UTF_8));

                bytes = line.getBytes(Charset.forName(CP866));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, CP866));

                bytes = line.getBytes(Charset.forName(CP1251));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, CP1251));

                bytes = line.getBytes(Charset.forName(KOI8_R));
                MAX_PATTERN_LENGTH = Math.max(MAX_PATTERN_LENGTH, bytes.length);
                patterns.add(new Pair(bytes, KOI8_R));
            }
        }

        MY_BUFFER_SIZE = Math.max(MY_BUFFER_SIZE, 8 * MAX_PATTERN_LENGTH);

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {

                    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file.toFile()));
                    int startOffset = 0;  // с какого места подчтывать новый кусок буфера
                    int lineBeginPos = 0; // с какого места начать сканировать
                    boolean cut = false; // правда ли что строка была разорвана и назад пути нет
                    byte[] text = new byte[MY_BUFFER_SIZE];  // буфер
                    int read; // сколько последний раз считалось
                    int nextNpos;


                    while ((read = bis.read(text, startOffset, MY_BUFFER_SIZE - startOffset)) != -1 || startOffset != 0) {
                        // перебираем паттерны
                        if (read == -1)
                            read = 0;
                        for (int pi = 0; pi < patterns.size(); ++pi) {
                            Pair<byte[], String> p = patterns.get(pi);
                            nextNpos = findEnd(text, lineBeginPos, read + startOffset, p.getValue());

                            // сканируем весь буффер кроме хвоста
                            for (int i = lineBeginPos; i < startOffset + read - MAX_PATTERN_LENGTH; ++i) {
                                boolean flag = true;

                                // проверяем, что конкретный паттерн найдется
                                for (int j = 0; j < p.getKey().length; ++j) {
                                    if (text[i + j] != p.getKey()[j] || i + j >= startOffset + read) {
                                        flag = false;
                                        break;
                                    }
                                }
                                if (flag) // found pattern p at position i
                                {
                                    System.out.print(startPath.relativize(file) + ": ");
                                    if (cut) {
                                        System.out.print("...");
                                    }
                                    int pos = i;
                                    // выведем его до символа \N, а потом подвинем вначало и прочитаем еще
                                    while (true) {
                                        int k = findEnd(text, pos, read + startOffset, p.getValue());
                                        System.out.print(sub(text, lineBeginPos, k, p.getValue()));

                                        if (k < read + startOffset) // поместилось в буфер
                                        {
                                            System.out.println(" @encoding: " + p.getValue());
                                            int len = sep.getBytes(Charset.forName(p.getValue())).length;
                                            System.arraycopy(text, k + len, text, 0, startOffset + read - (k + len)); // подвигаем
                                            startOffset = read + startOffset - (k + len); // валидный кусок в начале, который мы не проверяли еще
                                            read = bis.read(text, startOffset, MY_BUFFER_SIZE - startOffset); // чтобы буфер был полный

                                            lineBeginPos = 0;
                                            pi = -1; // начнем искать паттерны с самого начала
                                            cut = false;
                                            break; // while(true)
                                        } else // длинная
                                        {
                                            read = bis.read(text, 0, MY_BUFFER_SIZE);
                                            startOffset = 0;
                                            lineBeginPos = 0;
                                            pos = lineBeginPos;
                                        }
                                    }
                                    break; // сканирование всего буфера кроме хвоста
                                    // pi снова будет 0, и мы просканируем новый буфер снова для каждого паттерна
                                } else if (i == nextNpos) {
                                    i += sep.getBytes(Charset.forName(p.getValue())).length;
                                    lineBeginPos = i;
                                    nextNpos = findEnd(text, i, read + startOffset, p.getValue());
                                }
                            }
                        }

                        // мы просканировали весь буфер кроме хвоста, но так ничего и не нашли.
                        // подвинем хыост в начало, дочитаем сколько влезет и еще раз посмотрим.
                        // НО! если в конце невалидные байты, сдвинем сколько есть и больше не трогает!!!!!!!!

                        //если файл закончился и буфер забит не полностью концом файла
                        if (read + startOffset != MY_BUFFER_SIZE) {
                            if (read + startOffset >= MY_BUFFER_SIZE - MAX_PATTERN_LENGTH) {
                                System.arraycopy(text, MY_BUFFER_SIZE - MAX_PATTERN_LENGTH, text, 0, startOffset + read - (MY_BUFFER_SIZE - MAX_PATTERN_LENGTH)); // подвигаем
                                startOffset = startOffset + read - (MY_BUFFER_SIZE - MAX_PATTERN_LENGTH); // валидный кусок в начале, который мы не проверяли еще
                            } else {
                                startOffset = startOffset + read;
                                if (read == -1)
                                    ++startOffset;
                            }

                            lineBeginPos = 0;
                            for (int pi = 0; pi < patterns.size(); ++pi) {
                                Pair<byte[], String> p = patterns.get(pi);
                                nextNpos = findEnd(text, 0, startOffset, p.getValue());

                                // сканируем весь буффер
                                for (int i = lineBeginPos; i < startOffset; ++i) {
                                    boolean flag = true;
                                    // проверяем, что конкретный паттерн найдется
                                    for (int j = 0; j < p.getKey().length; ++j) {
                                        if (text[i + j] != p.getKey()[j] || i + j >= startOffset) {
                                            flag = false;
                                            break;
                                        }
                                    }
                                    if (flag) // found pattern p at position i
                                    {
                                        System.out.print(startPath.relativize(file) + ": ");
                                        if (cut) {
                                            System.out.print("...");
                                        }
                                        int pos = i;
                                        // выведем его до нулевого символа, а потом подвинем в начало

                                        int k = findEnd(text, pos, startOffset, p.getValue());
                                        System.out.print(sub(text, lineBeginPos, k, p.getValue()));


                                        System.out.println(" @encoding: " + p.getValue());
                                        lineBeginPos = k;
                                        startOffset = startOffset - (k + 1); // валидный кусок в начале, который мы не проверяли еще
                                        pi = -1; // начнем искать паттерны с самого начала
                                        cut = false;
                                        break; // сканирование всего буфера кроме хвоста
                                        // pi снова будет 0, и мы просканируем новый буфер снова для каждого паттерна
                                    }

                                    if (i == nextNpos) {
                                        i += sep.getBytes(Charset.forName(p.getValue())).length;
                                        lineBeginPos = i;
                                        nextNpos = findEnd(text, i, startOffset, p.getValue());
                                    }
                                }
                            }
                            break;

                        } else {
                            System.arraycopy(text, MY_BUFFER_SIZE - MAX_PATTERN_LENGTH, text, 0, MAX_PATTERN_LENGTH); // подвигаем
                            startOffset = MAX_PATTERN_LENGTH; // валидный кусок в начале, который мы не проверяли еще
                            lineBeginPos = 0;
                            cut = true;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            System.out.println(IO_ERROR + "in Files");
            System.exit(1);
        }
    }

    static int findEnd(byte[] t, int begin, int end, String enc) {
        byte[] sepbytes = sep.getBytes(Charset.forName(enc));
        int sepsize = sepbytes.length;
        for (int i = begin + 1; i < end - sepsize + 1; ++i) {
            boolean flag = true;
            for (int j = 0; j < sepsize; ++j) {
                if (t[i + j] != sepbytes[j])
                    flag = false;
            }
            if (flag)
                return i;
        }
        return end;
    }

    static String sub(byte[] t, int begin, int end, String enc) {
        int len = end - begin;

        byte[] a = new byte[len];
        System.arraycopy(t, begin, a, 0, len);
        String res = new String(a, Charset.forName(enc));
        return res;
    }
}