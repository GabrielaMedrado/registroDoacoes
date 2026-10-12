package br.org.iap.doacoes.comum;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * CSV para abrir no Excel em português: separador ";", fim de linha CRLF e UTF-8 com BOM (sem ele, o Excel
 * mostra os acentos errados).
 */
public class CsvWriter {

	private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

	private final StringBuilder conteudo = new StringBuilder();

	public CsvWriter linha(String... celulas) {
		for (int i = 0; i < celulas.length; i++) {
			if (i > 0) {
				conteudo.append(';');
			}
			conteudo.append(celula(celulas[i]));
		}
		conteudo.append("\r\n");
		return this;
	}

	public byte[] paraBytes() {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		saida.writeBytes(BOM);
		saida.writeBytes(conteudo.toString().getBytes(StandardCharsets.UTF_8));
		return saida.toByteArray();
	}

	private static String celula(String valor) {
		if (valor == null || valor.isEmpty()) {
			return "";
		}
		String texto = valor;
		// Impede que a planilha interprete o conteúdo como fórmula (injeção de CSV).
		if ("=+-@".indexOf(texto.charAt(0)) >= 0) {
			texto = "'" + texto;
		}
		if (texto.contains(";") || texto.contains("\"") || texto.contains("\r") || texto.contains("\n")) {
			texto = "\"" + texto.replace("\"", "\"\"") + "\"";
		}
		return texto;
	}
}
