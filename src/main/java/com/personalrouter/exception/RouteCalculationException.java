package com.personalrouter.exception;

/** Lançada quando o OpenRouteService não retorna uma rota calculável para os pontos informados. */
public class RouteCalculationException extends RuntimeException {

    public RouteCalculationException(String message) {
        super(message);
    }

    public RouteCalculationException(String message, Throwable cause) {
        super(message, cause);
    }
}
