package br.org.iap.doacoes.comum;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Números e datas em pt-BR para textos gerados no servidor (CSV e WhatsApp).
 * Não usa NumberFormat.getCurrencyInstance: ele separa "R$" com espaço não separável.
 */
public final class Formatos {

	private static final DecimalFormatSymbols PT_BR = DecimalFormatSymbols.getInstance(Locale.of("pt", "BR"));
	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private Formatos() {
	}

	/** Quantidade com até 2 casas e sem separador de milhar: 2,5 · 1500. */
	public static String quantidade(BigDecimal valor) {
		return new DecimalFormat("0.##", PT_BR).format(valor);
	}

	/** Número para planilha: sempre 2 casas, sem separador de milhar (1234,50). */
	public static String numero(BigDecimal valor) {
		return new DecimalFormat("0.00", PT_BR).format(valor);
	}

	/** R$ 1.234,50 */
	public static String moeda(BigDecimal valor) {
		return "R$ " + new DecimalFormat("#,##0.00", PT_BR).format(valor);
	}

	public static String data(LocalDate data) {
		return DATA.format(data);
	}
}
