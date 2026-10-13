package br.org.iap.doacoes.comum;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	public record ErroCampo(String campo, String mensagem) {
	}

	public static ResponseEntity<ProblemDetail> problema(HttpStatus status, String detalhe) {
		return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detalhe));
	}

	private static ResponseEntity<ProblemDetail> errosDeCampo(List<ErroCampo> erros) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dados inválidos.");
		pd.setProperty("erros", erros);
		return ResponseEntity.badRequest().body(pd);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> validacao(MethodArgumentNotValidException ex) {
		return errosDeCampo(ex.getBindingResult().getFieldErrors().stream()
				.map(e -> new ErroCampo(e.getField(), e.getDefaultMessage()))
				.toList());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ProblemDetail> corpoIlegivel(HttpMessageNotReadableException ex) {
		return problema(HttpStatus.BAD_REQUEST, "Requisição inválida.");
	}

	@ExceptionHandler(CampoInvalidoException.class)
	public ResponseEntity<ProblemDetail> campoInvalido(CampoInvalidoException ex) {
		return errosDeCampo(List.of(new ErroCampo(ex.getCampo(), ex.getMessage())));
	}

	@ExceptionHandler(RegraNegocioException.class)
	public ResponseEntity<ProblemDetail> regraNegocio(RegraNegocioException ex) {
		return problema(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(AcessoNegadoException.class)
	public ResponseEntity<ProblemDetail> acessoNegado(AcessoNegadoException ex) {
		return problema(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	@ExceptionHandler(NaoEncontradoException.class)
	public ResponseEntity<ProblemDetail> naoEncontrado(NaoEncontradoException ex) {
		return problema(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/** Duas gravações da mesma versão ao mesmo tempo: a segunda perde (@Version). */
	@ExceptionHandler(ObjectOptimisticLockingFailureException.class)
	public ResponseEntity<ProblemDetail> conflitoDeVersao(ObjectOptimisticLockingFailureException ex) {
		return problema(HttpStatus.CONFLICT,
				"Este registro foi alterado por outra pessoa. Recarregue a página e tente de novo.");
	}
}
