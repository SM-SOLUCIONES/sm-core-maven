package com.blue.open.Core.Kernel.Configuracion;

import com.blue.open.Core.Kernel.Puertos.ProveedorDeVersion;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Agrega el header API-Version a todas las respuestas HTTP, coincidente con la version expuesta en /info
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
@RequiredArgsConstructor
public class EncabezadoApiVersionFilter extends OncePerRequestFilter {

    private static final String NOMBRE_DEL_ENCABEZADO = "API-Version";

    private final ProveedorDeVersion proveedorDeVersion;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        response.setHeader(NOMBRE_DEL_ENCABEZADO, proveedorDeVersion.getVersion());
        filterChain.doFilter(request, response);
    }
}
