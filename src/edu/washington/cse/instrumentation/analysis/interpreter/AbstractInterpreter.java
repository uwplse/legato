package edu.washington.cse.instrumentation.analysis.interpreter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AbstractInterpreter<N> {
	private final OperationProvider<N> op;
	private final DomainParser<N> parser;

	public AbstractInterpreter(final DomainParser<N> parser, final OperationProvider<N> op) {
		this.parser = parser;
		this.op = op;
	}
	
	public void interpret(final String file) throws IOException {
		final ArrayList<String> lines = new ArrayList<>();
		try(
				BufferedReader br = new BufferedReader(new FileReader(new File(file)));
			) {
				String l;
				while((l = br.readLine()) != null) {
					lines.add(l.trim());
				}
			}
			final Map<String, String> expanded = new HashMap<>(lines.size());
			int i = 0;
			while(i < lines.size()) {
				final String l = lines.get(i++);
				if(l.equals("--")) {
					break;
				}
				if(l.length() == 0) {
					continue;
				}
				final String[] t = l.split(":\\s+");
				assert t.length == 2 : l;
				final String variableName = t[0];
				assert !expanded.containsKey(variableName);
				final String subst = expand(t[1], expanded);
				expanded.put(variableName, subst);
			}
			final Map<String, N> varMap = new HashMap<>();
			for(final Map.Entry<String, String> kv : expanded.entrySet()) {
				varMap.put(kv.getKey(), parser.parse(kv.getValue()));
			}
			final List<N> resultStack = new ArrayList<>();
			interpret: while(i < lines.size()) {
				final String l = lines.get(i++);
				if(l.startsWith("//")) {
					continue;
				}
				if(l.isEmpty()) {
					continue;
				}
				final String[] tok = l.split("\\s+");
				final int sz = resultStack.size();
				switch(tok[0]) {
				case "quit":
					break interpret;
				case "print":
					if(tok.length == 1) {
						System.out.println(resultStack.get(sz - 1));
					} else {
						for(int j = 1; j < tok.length; j++) {
							varMap.containsKey(tok[j]);
							System.out.println(varMap.get(tok[j]));
						}
					}
					break;
				case "join":
					if(tok.length == 1) {
						assert sz >= 2;
						final N n1 = resultStack.remove(sz - 1);
						final N n2 = resultStack.remove(sz - 2);
						resultStack.add(op.join(n1, n2));
					} else {
						N n = varMap.get(tok[1]);
						for(int j = 2; j < tok.length; j++) {
							final N res = op.join(n, varMap.get(tok[j]));
							if(res == null) {
								System.out.println("Join failed");
							}
							n = res;
						}
						resultStack.add(n);
					}
					break;
				case "store":
					assert sz > 0;
					assert tok.length == 2;
					assert !varMap.containsKey(tok[1]);
					varMap.put(tok[1], resultStack.remove(sz - 1));
					break;
				case "equal":
					final N n1;
					final N n2;
					if(tok.length == 1) {
						assert sz >= 2;
						n1 = resultStack.remove(sz - 1);
						n2 = resultStack.remove(sz - 2);
					} else {
						n1 = varMap.get(tok[1]);
						n2 = varMap.get(tok[2]);
					}
					System.out.println(op.equal(n1, n2));
					break;
				case "pop":
					assert sz > 0;
					resultStack.remove(sz - 1);
				case "debug":
					System.out.println(l.replaceAll("^debug\\s+", ""));
					break;
				case "dump-stack":
					System.out.println(">> Top of stack listed last...");
					for(int j = 0; j < sz; j++) {
						System.out.println(">> Position " + j + " = " + resultStack.get(j));
					}
					break;
				case "dup":
					assert resultStack.size() > 0;
					resultStack.add(resultStack.get(resultStack.size() - 1));
					break;
				case "dump-variables":
					for(final Map.Entry<String, N> kv : varMap.entrySet()) {
						System.out.println(">> " + kv.getKey() + " = " + kv.getValue());
					}
					break;
				default:
					if(!op.extCommand(tok, resultStack, varMap)) {
						throw new RuntimeException("Unrecognized command: " + l);
					}
					break;
				}
			}
	}
	
	private static Pattern varReferencePattern = Pattern.compile("\\[([^]]+)\\]");

	private static String expand(final String string, final Map<String, String> expanded) {
		final StringBuffer sb = new StringBuffer();
		final Matcher m = varReferencePattern.matcher(string);
		while(m.find()) {
			final String varName = m.group(1);
			if(!expanded.containsKey(varName)) {
				throw new IllegalArgumentException("Could not find reference to variable: " + varName);
			}
			m.appendReplacement(sb, expanded.get(varName));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
