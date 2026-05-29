package com.routeoptimizer.controller;

import com.routeoptimizer.model.entity.User;
import com.routeoptimizer.model.enums.Role;
import com.routeoptimizer.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping({"/api/v1/reports", "/reports"})
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sender") String sender,
            @RequestParam("type") String type) {
        
        return ResponseEntity.ok(reportService.uploadDocument(file, sender, type));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable String id) {
        byte[] pdfBytes = reportService.generateTransactionReport(id);
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"reporte_" + id + ".pdf\"")
                .body(pdfBytes);
    }

    @GetMapping("/generate-global")
    public ResponseEntity<byte[]> generateGlobalReport(@RequestParam(required = false) String city) {
        if (isForbiddenForLogistics(city)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        byte[] pdfBytes = reportService.generateGlobalReport();
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"Cierre_Global_VibeRoute.pdf\"")
                .body(pdfBytes);
    }

    @GetMapping("/generate-word")
    public ResponseEntity<byte[]> generateOrdersReportWord(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String driver,
            @RequestParam(required = false) String search) {
        if (isForbiddenForLogistics(city)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String cityFilter = resolveCity(city);
        byte[] wordBytes = reportService.generateOrdersReportWord(cityFilter, status, driver, search);
        
        String filename = (cityFilter != null && !cityFilter.trim().isEmpty() && !cityFilter.equalsIgnoreCase("undefined")) 
            ? "Lista_Pedidos_" + cityFilter.trim() + ".docx" 
            : "Reporte_Operativo_Pedidos.docx";
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(wordBytes);
    }

    @GetMapping("/generate-excel")
    public ResponseEntity<byte[]> generateOrdersReportExcel(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String driver,
            @RequestParam(required = false) String search) {
        if (isForbiddenForLogistics(city)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String cityFilter = resolveCity(city);
        byte[] excelBytes = reportService.generateOrdersReportExcel(cityFilter, status, driver, search);
        
        // Eliminamos el fallback global para el logístico. 
        // Si no hay ciudad, el nombre será genérico, pero el contenido ya está filtrado en el service.
        String filename = (cityFilter != null && !cityFilter.trim().isEmpty() && !cityFilter.equalsIgnoreCase("undefined")) 
            ? "Lista_Pedidos_" + cityFilter.trim() + ".xlsx" 
            : "Reporte_Operativo_Pedidos.xlsx";
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(excelBytes);
    }

    @GetMapping("/generate-general-word")
    public ResponseEntity<byte[]> generateGeneralReportWord(@RequestParam(required = false) String city) {
        if (isForbiddenForLogistics(city)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String cityFilter = resolveCity(city);
        byte[] wordBytes = reportService.generateGeneralReportWord(cityFilter);
        
        String filename = (cityFilter != null && !cityFilter.isEmpty()) ? "Reporte_Estrategico_" + cityFilter + ".docx" : "Reporte_Estrategico_Global.docx";
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(wordBytes);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listDocuments() {
        return ResponseEntity.ok(reportService.listDocuments());
    }

    private String resolveCity(String requestedCity) {
        User user = authenticatedUser();
        if (user != null && user.getRole() == Role.LOGISTICS) {
            return user.getAssignedCity();
        }
        return requestedCity;
    }

    private boolean isForbiddenForLogistics(String requestedCity) {
        User user = authenticatedUser();
        return user != null
                && user.getRole() == Role.LOGISTICS
                && requestedCity != null
                && !requestedCity.trim().isEmpty()
                && !requestedCity.equalsIgnoreCase("undefined")
                && !requestedCity.equalsIgnoreCase(user.getAssignedCity());
    }

    private User authenticatedUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User user ? user : null;
    }
}
