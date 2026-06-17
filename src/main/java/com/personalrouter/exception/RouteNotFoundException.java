package com.personalrouter.exception;

/** Lançada quando uma rota salva não é encontrada pelo identificador informado. */
public class RouteNotFoundException extends RuntimeException {

    public RouteNotFoundException(String message) {
        super(message);
    }
}
