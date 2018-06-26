package edu.washington.cse.instrumentation.analysis.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

import org.yaml.snakeyaml.Yaml;

public class YamlUtil {

	@SuppressWarnings("unchecked")
	public static <T> T unsafeLoadFromFile(final String fileName) {
		final Yaml y = new Yaml();
		T parsed;
		try(final InputStream is = new FileInputStream(new File(fileName))) {
			parsed = (T) y.load(is);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
		return parsed;
	}
	
	public static void dumpYaml(final Object toDump) {
		final Yaml y = new Yaml();
		System.out.println(y.dump(toDump));
	}
	
	public static void dumpYaml(final Object toDump, final String outputFile) {
		final Yaml y = new Yaml();
		try(FileWriter fw = new FileWriter(new File(outputFile))) {
			y.dump(toDump, fw);
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

}
