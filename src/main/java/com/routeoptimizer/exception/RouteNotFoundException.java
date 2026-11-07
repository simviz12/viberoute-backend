package com.routeoptimizer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Excepción lanzada cuando no se encuentra una ruta activa o existente para un conductor.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RouteNotFoundException extends ResourceNotFoundException {

    public RouteNotFoundException(String message) {
        super(message);
    }
}

