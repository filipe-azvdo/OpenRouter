package com.personalrouter.controller;

import com.personalrouter.dto.PlannedRouteDto;
import com.personalrouter.dto.RoutePlanRequest;
import com.personalrouter.dto.RouteResultDto;
import com.personalrouter.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Slf4j
@RestController
@RequestMapping("/api/v1/routes")
@Tag(name = "Rotas", description = "Gerenciamento de rotas planejadas")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @Operation(summary = "Calcula uma rota sem persistir (preview)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rota calculada com sucesso",
                content = @Content(schema = @Schema(implementation = RouteResultDto.class))),
        @ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "422", description = "Rota não pôde ser calculada"),
        @ApiResponse(responseCode = "503", description = "Serviço de roteamento indisponível")
    })
    @PostMapping("/plan")
    public ResponseEntity<RouteResultDto> planRoute(@Valid @RequestBody RoutePlanRequest request) {
        log.info("Planning route from {} to {}", request.origin(), request.destination());
        return ResponseEntity.ok(routeService.planRoute(request));
    }

    @Operation(summary = "Calcula e persiste uma nova rota")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Rota criada com sucesso",
                content = @Content(schema = @Schema(implementation = PlannedRouteDto.class))),
        @ApiResponse(responseCode = "400", description = "Requisição inválida"),
        @ApiResponse(responseCode = "422", description = "Rota não pôde ser calculada"),
        @ApiResponse(responseCode = "503", description = "Serviço de roteamento indisponível")
    })
    @PostMapping
    public ResponseEntity<PlannedRouteDto> createRoute(@Valid @RequestBody RoutePlanRequest request) {
        log.info("Creating route from {} to {}", request.origin(), request.destination());
        PlannedRouteDto created = routeService.createRoute(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Lista todas as rotas salvas")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso",
                content = @Content(array = @ArraySchema(schema = @Schema(implementation = PlannedRouteDto.class))))
    })
    @GetMapping
    public ResponseEntity<List<PlannedRouteDto>> listRoutes() {
        log.debug("Listing all routes");
        return ResponseEntity.ok(routeService.listRoutes());
    }

    @Operation(summary = "Retorna uma rota salva pelo identificador")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rota encontrada",
                content = @Content(schema = @Schema(implementation = PlannedRouteDto.class))),
        @ApiResponse(responseCode = "404", description = "Rota não encontrada")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PlannedRouteDto> getRoute(@PathVariable UUID id) {
        log.debug("Fetching route {}", id);
        return ResponseEntity.ok(routeService.getRoute(id));
    }

    @Operation(summary = "Remove uma rota salva pelo identificador")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Rota removida com sucesso"),
        @ApiResponse(responseCode = "404", description = "Rota não encontrada")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID id) {
        log.info("Deleting route {}", id);
        routeService.deleteRoute(id);
        return ResponseEntity.noContent().build();
    }
}
