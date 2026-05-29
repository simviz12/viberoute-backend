package com.routeoptimizer.controller;

import com.routeoptimizer.service.ReportService;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<byte[]> generateGlobalReport() {
        byte[] pdfBytes = reportService.generateGlobalReport();
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=\"Cierre_Global_VibeRoute.pdf\"")
                .body(pdfBytes);
    }

    @GetMapping("/generate-word")
    public ResponseEntity<byte[]> generateOrdersReportWord(@RequestParam(required = false) String city) {
        byte[] wordBytes = reportService.generateOrdersReportWord(city);
        
        String filename = (city != null && !city.trim().isEmpty() && !city.equalsIgnoreCase("undefined")) 
            ? "Lista_Pedidos_" + city.trim() + ".docx" 
            : "Reporte_Operativo_Pedidos.docx";
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(wordBytes);
    }

    @GetMapping("/generate-excel")
    public ResponseEntity<byte[]> generateOrdersReportExcel(@RequestParam(required = false) String city) {
        byte[] excelBytes = reportService.generateOrdersReportExcel(city);
        
        // Eliminamos el fallback global para el logístico. 
        // Si no hay ciudad, el nombre será genérico, pero el contenido ya está filtrado en el service.
        String filename = (city != null && !city.trim().isEmpty() && !city.equalsIgnoreCase("undefined")) 
            ? "Lista_Pedidos_" + city.trim() + ".xlsx" 
            : "Reporte_Operativo_Pedidos.xlsx";
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(excelBytes);
    }

    @GetMapping("/generate-general-word")
    public ResponseEntity<byte[]> generateGeneralReportWord(@RequestParam(required = false) String city) {
        byte[] wordBytes = reportService.generateGeneralReportWord(city);
        
        String filename = (city != null && !city.isEmpty()) ? "Reporte_Estrategico_" + city + ".docx" : "Reporte_Estrategico_Global.docx";
        
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(wordBytes);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listDocuments() {
        return ResponseEntity.ok(reportService.listDocuments());
    }
}
