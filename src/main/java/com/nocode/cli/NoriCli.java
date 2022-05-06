package com.nocode.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.cli.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.ko.KoreanTokenizer;
import org.apache.lucene.analysis.ko.POS;
import org.apache.lucene.analysis.ko.dict.UserDictionary;
import org.apache.lucene.analysis.ko.tokenattributes.PartOfSpeechAttribute;
import org.apache.lucene.analysis.ko.tokenattributes.ReadingAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.*;
import java.util.*;

public class NoriCli {

    private Analyzer analyzer;

    public NoriCli(KoreanTokenizer.DecompoundMode mode, UserDictionary userDict) {
        final Set<POS.Tag> DEFAULT_STOP_TAGS =
                new HashSet<>(
                        Arrays.asList(
                                POS.Tag.UNA,
                                POS.Tag.NA));
        this.analyzer =
                new KoreanAnalyzer(
                        userDict,
                        mode,
                        DEFAULT_STOP_TAGS,
                        false);
    }

    enum OutputFormat {
        MECAB,
        JSON
    }

    List<Token> tokenize(String text) throws IOException {
        // tokenize text
        TokenStream tokenStream = this.analyzer.tokenStream("ignored", new StringReader(text));
        CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
        PartOfSpeechAttribute partOfSpeechAttribute = tokenStream.getAttribute(PartOfSpeechAttribute.class);
        ReadingAttribute readingAttribute = tokenStream.getAttribute(ReadingAttribute.class);

        List<Token> tokens = new ArrayList<>();

        tokenStream.reset();
        while (tokenStream.incrementToken()) {
            String surface = charTermAttribute.toString();

            ArrayList<String> attrs = new ArrayList<>();
            String pos = partOfSpeechAttribute.getRightPOS().name();
            attrs.add(pos);
            for (int i = attrs.size(); i < 3; i++) {
                attrs.add("*");
            }
            attrs.add(surface);
            for (int i = attrs.size(); i < 7; i++) {
                attrs.add("*");
            }
            if (readingAttribute.getReading() == null) {
                attrs.add("*");
            } else {
                attrs.add(readingAttribute.getReading());
            }
            tokens.add(new Token(surface, attrs));
        }
        tokenStream.close();

        return tokens;

    }

    void print(List<Token> tokens, OutputFormat outputFormat) {
        switch (outputFormat) {
            case MECAB:
                for (Token token : tokens) {
                    System.out.printf("%s\t%s\n", token.getSurface(), String.join(",", token.getAttrs()));
                }
                System.out.println("EOS");
                break;
            case JSON:
                ObjectMapper mapper = new ObjectMapper();
                try {
                    System.out.println(mapper.writeValueAsString(tokens));
                } catch (JsonProcessingException e) {
                    System.out.println(e.getMessage());
                }
                break;
            default:
                for (Token token : tokens) {
                    System.out.printf("%s\t%s\n", token.getSurface(), String.join(",", token.getAttrs()));
                }
                System.out.println("EOS");
                break;
        }

    }

    public static void main(String args[]) {
        Options options = new Options();
        options.addOption(
                Option.builder("m")
                        .longOpt("tokenize-mode")
                        .hasArg()
                        .desc("The tokenization mode. `none`, `discard` or `mixed` can be specified. If not specified, use `discard` as default mode.")
                        .build()
        );
        options.addOption(
                Option.builder("o")
                        .longOpt("output-format")
                        .hasArg()
                        .desc("The output format. `mecab` or `json` can be specified. If not specified, use `mecab` as default mode.")
                        .build()
        );
        options.addOption(
                Option.builder("u")
                        .longOpt("user dictionary path")
                        .hasArg()
                        .desc("Specifies the file path of the user dictionary. default none.")
                        .build()
        );
        options.addOption(
                Option.builder("v")
                        .longOpt("version")
                        .desc("Print version.")
                        .build()
        );
        options.addOption(
                Option.builder("h")
                        .longOpt("help")
                        .desc("Print this message.")
                        .build()
        );

        HelpFormatter hf = new HelpFormatter();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            hf.printHelp(String.format("%s [OPTIONS] [INPUT_FILE]", NoriCli.class.getPackage().getImplementationTitle()), options);
            return;
        }
        if (cmd.hasOption("h")) {
            hf.printHelp(String.format("%s [OPTIONS] [INPUT_FILE]", NoriCli.class.getPackage().getImplementationTitle()), options);
            return;
        }
        if (cmd.hasOption("v")) {
            System.out.println(NoriCli.class.getPackage().getImplementationVersion());
            return;
        }

        // mode
        KoreanTokenizer.DecompoundMode mode = KoreanTokenizer.DecompoundMode.DISCARD;
        if (cmd.hasOption("m")) {
            switch (cmd.getOptionValue("m")) {
                case "none":
                    mode = KoreanTokenizer.DecompoundMode.NONE;
                    break;
                case "discard":
                    mode = KoreanTokenizer.DecompoundMode.DISCARD;
                    break;
                case "mixed":
                    mode = KoreanTokenizer.DecompoundMode.MIXED;
                    break;
                default:
                    System.out.printf("unexpected tokenization mode: %s\n", cmd.getOptionValue("m"));
                    hf.printHelp(String.format("%s [OPTIONS] [INPUT_FILE]", NoriCli.class.getPackage().getImplementationTitle()), options);
                    return;
            }
        }

        // output format
        OutputFormat outputFormat = OutputFormat.MECAB;
        if (cmd.hasOption("o")) {
            switch (cmd.getOptionValue("o")) {
                case "mecab":
                    outputFormat = OutputFormat.MECAB;
                    break;
                case "json":
                    outputFormat = OutputFormat.JSON;
                    break;
                default:
                    System.out.printf("unexpected output format: %s\n", cmd.getOptionValue("o"));
                    hf.printHelp(String.format("%s [OPTIONS] [INPUT_FILE]", NoriCli.class.getPackage().getImplementationTitle()), options);
                    return;
            }
        }

        // User dictionary
        UserDictionary userDictionary = null;
        if (cmd.hasOption("u")) {
            String userDictionaryPath = cmd.getOptionValue("u");
            File dictionaryFile = new File(userDictionaryPath);
            if(dictionaryFile.exists()) {
                try {
                    BufferedReader br
                            = new BufferedReader(new FileReader(dictionaryFile));
                    userDictionary = UserDictionary.open(br);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                System.out.printf("unexpected user dictionary file: %s\n", cmd.getOptionValue("u"));
                hf.printHelp(String.format("%s [OPTIONS] [INPUT_FILE]", NoriCli.class.getPackage().getImplementationTitle()), options);
                return;
            }
        }

        NoriCli cli = new NoriCli(mode, userDictionary);

        // read file
        if (cmd.getArgs().length > 0) {
            try {
                FileReader fileReader = new FileReader(new File(cmd.getArgs()[0]));
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    List<Token> tokens = cli.tokenize(line);
                    cli.print(tokens, outputFormat);
                }
                bufferedReader.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
            return;
        }

        // read stdin
        try {
            Scanner stdin = new Scanner(System.in);
            while (stdin.hasNextLine()) {
                String text = stdin.nextLine();
                List<Token> tokens = cli.tokenize(text);
                cli.print(tokens, outputFormat);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}
