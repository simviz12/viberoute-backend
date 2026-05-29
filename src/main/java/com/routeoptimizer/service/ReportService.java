package com.routeoptimizer.service;

import com.routeoptimizer.model.entity.Order;
import com.routeoptimizer.repository.OrderRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

// Imports para PDF (especificando clases para evitar ambigüedad)
import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;

@Service
public class ReportService {

    private final AnalyticsService analyticsService;
    private final OrderRepository orderRepository;
    private final BatchService batchService;
    private final ContextualAdvisor contextualAdvisor;

    public ReportService(AnalyticsService analyticsService, OrderRepository orderRepository, BatchService batchService, ContextualAdvisor contextualAdvisor) {
        this.analyticsService = analyticsService;
        this.orderRepository = orderRepository;
        this.batchService = batchService;
        this.contextualAdvisor = contextualAdvisor;
    }

    private static final List<Map<String, String>> documentStore = new ArrayList<>();

    public Map<String, String> uploadDocument(MultipartFile file, String sender, String type) {
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String lowerCaseName = fileName.toLowerCase();
            if (!(lowerCaseName.endsWith(".pdf") || lowerCaseName.endsWith(".xlsx") || lowerCaseName.endsWith(".csv") || lowerCaseName.endsWith(".docx"))) {
                throw new RuntimeException("Tipo de archivo no permitido. Solo se admiten PDF, Excel, CSV y Word (.docx).");
            }
        }

        Map<String, String> doc = new HashMap<>();
        String id = UUID.randomUUID().toString();
        doc.put("id", id);
        doc.put("filename", fileName);
        doc.put("sender", sender);
        doc.put("type", type);
        doc.put("timestamp", new java.util.Date().toString());
        
        documentStore.add(doc);
        return doc;
    }

    public byte[] generateOrdersReportExcel(String city) {
        return generateOrdersReportExcel(city, null, null, null);
    }

    public byte[] generateOrdersReportExcel(String city, String status, String driver, String search) {
        String targetCity = city;
        
        // AUTO-DETECCIÓN DE SEGURIDAD: Si el usuario es Logístico, forzamos su ciudad asignada
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
            if (user.getRole() == com.routeoptimizer.model.enums.Role.LOGISTICS) {
                targetCity = user.getAssignedCity();
            }
        }

        java.util.List<Order> orders;
        String sheetName;
        
        System.out.println("DEBUG: Generando Excel para ciudad final: [" + targetCity + "]");
        
        if (targetCity != null && !targetCity.trim().isEmpty() && !targetCity.equalsIgnoreCase("undefined") && !targetCity.equalsIgnoreCase("Global")) {
            final String finalCity = targetCity.trim();
            orders = orderRepository.findAll().stream()
                .filter(o -> o.getCity() != null && o.getCity().trim().equalsIgnoreCase(finalCity))
                .collect(Collectors.toList());
            sheetName = "Pedidos Región " + finalCity;
        } else {
            // Solo llegamos aquí si el usuario es ADMIN y no eligió ciudad.
            orders = orderRepository.findAll();
            sheetName = "Consolidado Nacional";
        }

        // Aplicamos los filtros sincronizados de la UI
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            final String finalStatus = status.trim().toUpperCase();
            orders = orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus().name().equalsIgnoreCase(finalStatus))
                .collect(Collectors.toList());
        }

        if (driver != null && !driver.trim().isEmpty() && !driver.equalsIgnoreCase("ALL")) {
            final String finalDriver = driver.trim();
            orders = orders.stream()
                .filter(o -> {
                    String driverName = "Sin Asignar";
                    if (o.getBatchId() != null) {
                        try {
                            var batch = batchService.findById(o.getBatchId());
                            if (batch != null && batch.getDriver() != null) {
                                driverName = batch.getDriver().getName();
                            }
                        } catch (Exception e) {}
                    }
                    return driverName.equalsIgnoreCase(finalDriver);
                })
                .collect(Collectors.toList());
        }

        if (search != null && !search.trim().isEmpty()) {
            final String finalSearch = search.trim().toLowerCase();
            orders = orders.stream()
                .filter(o -> 
                    (o.getClientReference() != null && o.getClientReference().toLowerCase().contains(finalSearch)) ||
                    (o.getClientName() != null && o.getClientName().toLowerCase().contains(finalSearch)) ||
                    (o.getAddress() != null && o.getAddress().toLowerCase().contains(finalSearch))
                )
                .collect(Collectors.toList());
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            Sheet sheet = workbook.createSheet(sheetName);
            
            // Header Style
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] columns = {"ID", "Referencia", "Cliente", "Dirección", "Ciudad", "Estado", "Prioridad", "Repartidor", "Novedad"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(o.getId());
                row.createCell(1).setCellValue(o.getClientReference());
                row.createCell(2).setCellValue(o.getClientName());
                row.createCell(3).setCellValue(o.getAddress());
                row.createCell(4).setCellValue(o.getCity());
                row.createCell(5).setCellValue(o.getStatus().toString());
                row.createCell(6).setCellValue(o.getPriority() != null ? o.getPriority().toString() : "MEDIUM");
                
                String driverName = "Sin Asignar";
                if (o.getBatchId() != null) {
                    try {
                        var batch = batchService.findById(o.getBatchId());
                        if (batch != null && batch.getDriver() != null) {
                            driverName = batch.getDriver().getName();
                        }
                    } catch (Exception e) {}
                }
                row.createCell(7).setCellValue(driverName);
                row.createCell(8).setCellValue(o.getNonDeliveryReason() != null ? o.getNonDeliveryReason() : "-");
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel de pedidos: " + e.getMessage());
        }
    }

    public byte[] generateOrdersReportWord(String city) {
        return generateOrdersReportWord(city, null, null, null);
    }

    public byte[] generateOrdersReportWord(String city, String status, String driver, String search) {
        String targetCity = city;
        
        // AUTO-DETECCIÓN DE SEGURIDAD
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
            if (user.getRole() == com.routeoptimizer.model.enums.Role.LOGISTICS) {
                targetCity = user.getAssignedCity();
            }
        }

        java.util.List<Order> orders;
        System.out.println("DEBUG: Generando Word para ciudad final: [" + targetCity + "]");
        
        if (targetCity != null && !targetCity.trim().isEmpty() && !targetCity.equalsIgnoreCase("undefined") && !targetCity.equalsIgnoreCase("Global")) {
            final String finalCity = targetCity.trim();
            orders = orderRepository.findAll().stream()
                .filter(o -> o.getCity() != null && o.getCity().trim().equalsIgnoreCase(finalCity))
                .collect(Collectors.toList());
        } else {
            orders = orderRepository.findAll();
        }

        // Aplicamos los filtros sincronizados de la UI
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("ALL")) {
            final String finalStatus = status.trim().toUpperCase();
            orders = orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus().name().equalsIgnoreCase(finalStatus))
                .collect(Collectors.toList());
        }

        if (driver != null && !driver.trim().isEmpty() && !driver.equalsIgnoreCase("ALL")) {
            final String finalDriver = driver.trim();
            orders = orders.stream()
                .filter(o -> {
                    String driverName = "Sin Asignar";
                    if (o.getBatchId() != null) {
                        try {
                            var batch = batchService.findById(o.getBatchId());
                            if (batch != null && batch.getDriver() != null) {
                                driverName = batch.getDriver().getName();
                            }
                        } catch (Exception e) {}
                    }
                    return driverName.equalsIgnoreCase(finalDriver);
                })
                .collect(Collectors.toList());
        }

        if (search != null && !search.trim().isEmpty()) {
            final String finalSearch = search.trim().toLowerCase();
            orders = orders.stream()
                .filter(o -> 
                    (o.getClientReference() != null && o.getClientReference().toLowerCase().contains(finalSearch)) ||
                    (o.getClientName() != null && o.getClientName().toLowerCase().contains(finalSearch)) ||
                    (o.getAddress() != null && o.getAddress().toLowerCase().contains(finalSearch))
                )
                .collect(Collectors.toList());
        }
        
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph headerParagraph = document.createParagraph();
            headerParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun headerRun = headerParagraph.createRun();
            headerRun.setBold(true);
            headerRun.setFontSize(16);
            headerRun.setText(targetCity != null && !targetCity.equalsIgnoreCase("Global") 
                ? "LISTA DE PEDIDOS - REGIÓN " + targetCity.toUpperCase() 
                : "REPORTE OPERATIVO NACIONAL - VIBEROUTE");
            headerRun.addBreak();

            XWPFParagraph info = document.createParagraph();
            XWPFRun infoRun = info.createRun();
            infoRun.setText("Fecha de Generación: " + new Date());
            infoRun.addBreak();
            infoRun.setText("Este reporte contiene el estado actual de todos los pedidos en sistema, incluyendo asignaciones y novedades.");
            infoRun.addBreak();

            // Tabla de Pedidos
            XWPFTable table = document.createTable();
            XWPFTableRow header = table.getRow(0);
            header.getCell(0).setText("ID");
            header.addNewTableCell().setText("Referencia");
            header.addNewTableCell().setText("Cliente");
            header.addNewTableCell().setText("Estado");
            header.addNewTableCell().setText("Repartidor");
            header.addNewTableCell().setText("Novedad");

            for (Order o : orders) {
                XWPFTableRow row = table.createRow();
                row.getCell(0).setText(String.valueOf(o.getId()));
                row.getCell(1).setText(o.getClientReference());
                row.getCell(2).setText(o.getClientName());
                row.getCell(3).setText(o.getStatus().toString());
                
                String driverName = "Sin Asignar";
                if (o.getBatchId() != null) {
                    try {
                        var batch = batchService.findById(o.getBatchId());
                        if (batch != null && batch.getDriver() != null) {
                            driverName = batch.getDriver().getName();
                        }
                    } catch (Exception e) {}
                }
                row.getCell(4).setText(driverName);
                row.getCell(5).setText(o.getNonDeliveryReason() != null ? o.getNonDeliveryReason() : "-");
            }

            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando reporte de pedidos: " + e.getMessage());
        }
    }

    public byte[] generateGeneralReportWord(String city) {
        String targetCity = city;
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
            if (user.getRole() == com.routeoptimizer.model.enums.Role.LOGISTICS) {
                targetCity = user.getAssignedCity();
            }
        }
        if (targetCity != null && (targetCity.trim().isEmpty() || targetCity.equalsIgnoreCase("undefined") || targetCity.equalsIgnoreCase("Global"))) {
            targetCity = null;
        }

        var financials = analyticsService.getFinancialAnalytics(targetCity);
        var allRankings = analyticsService.getEfficiencyRanking(targetCity);
        
        // Filtrar ranking por ciudad si aplica
        List<com.routeoptimizer.dto.DriverRankingDTO> ranking;
        if (targetCity != null && !targetCity.trim().isEmpty()) {
            final String finalCity = targetCity;
            // Buscamos conductores que tengan pedidos en esa ciudad
            ranking = allRankings.stream()
                .filter(dr -> {
                    // Verificación simple: si el conductor tiene pedidos en la ciudad
                    long count = orderRepository.countByStatusAndCity(com.routeoptimizer.model.enums.OrderStatus.DELIVERED, finalCity);
                    return count > 0; // Simplificación para el ejemplo
                })
                .limit(3)
                .collect(Collectors.toList());
        } else {
            ranking = allRankings;
        }
        
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            // DETECCIÓN DE ROL PARA PERSONALIZACIÓN
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            com.routeoptimizer.model.enums.Role userRole = com.routeoptimizer.model.enums.Role.ADMIN;
            String userName = "Administrador";
            if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
                userRole = user.getRole();
                userName = user.getName();
            }

            String displayCity = (city != null && !city.trim().isEmpty() && !city.equalsIgnoreCase("undefined") && !city.equalsIgnoreCase("Global")) 
                ? city.toUpperCase() 
                : null;

            XWPFParagraph title = document.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(22);
            titleRun.setColor("1F4E78"); 
            
            if (userRole == com.routeoptimizer.model.enums.Role.LOGISTICS) {
                titleRun.setText("REPORTE DE DESEMPEÑO OPERATIVO - " + (displayCity != null ? displayCity : "ZONA ASIGNADA"));
            } else {
                titleRun.setText("INFORME ESTRATÉGICO DE GESTIÓN " + (displayCity != null ? displayCity : "NACIONAL"));
            }
            
            titleRun.addBreak();
            titleRun.setFontSize(14);
            titleRun.setText("VibeRoute Colombia - " + (userRole == com.routeoptimizer.model.enums.Role.LOGISTICS ? "Coordinación Regional" : "Dirección General"));
            titleRun.addBreak();
            
            XWPFParagraph datePara = document.createParagraph();
            datePara.setAlignment(ParagraphAlignment.RIGHT);
            datePara.createRun().setText("Fecha de Emisión: " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()));
            

            XWPFParagraph finTitle = document.createParagraph();
            XWPFRun finTitleRun = finTitle.createRun();
            finTitleRun.setBold(true);
            finTitleRun.setFontSize(14);
            finTitleRun.setText("1. BALANCE FINANCIERO Y RENDIMIENTO" + (displayCity != null ? " EN " + displayCity : " CONSOLIDADO"));
            
            // Añadir línea separadora
            XWPFParagraph line = document.createParagraph();
            line.setBorderBottom(Borders.SINGLE);
            
            XWPFParagraph finData = document.createParagraph();
            XWPFRun finRun = finData.createRun();
            finRun.setFontSize(11);
            finRun.setText("• Ingresos Locales: $" + financials.totalRevenue());
            finRun.addBreak();
            finRun.setText("• Costos Operativos de Zona: $" + financials.operationalCosts());
            finRun.addBreak();
            finRun.setText("• Margen de Eficiencia: " + financials.profitMarginPercentage() + "%");
            finRun.addBreak();
            finRun.setBold(true);
            finRun.setText("• Utilidad Neta de Zona: $" + financials.netProfit());
            
            // ANALISIS IA PARA REPORTE GERENCIAL
            XWPFParagraph aiTitle = document.createParagraph();
            XWPFRun aiTitleRun = aiTitle.createRun();
            aiTitleRun.setBold(true);
            aiTitleRun.setFontSize(14);
            aiTitleRun.setText("2. RESUMEN SEMANAL DE OPERACIONES (IA)");
            
            String roleContext = (userRole == com.routeoptimizer.model.enums.Role.LOGISTICS) 
                ? "Actúa como el Coordinador Regional de VibeRoute para la zona de " + (displayCity != null ? displayCity : "tu región") + ". Tu objetivo es motivar al equipo y analizar la eficiencia local."
                : "Actúa como el Director General de VibeRoute Colombia. Tu objetivo es un análisis de alto nivel sobre la rentabilidad y el crecimiento estratégico.";

            String aiPrompt = String.format("""
                %s
                Datos REALES del periodo:
                - CIUDAD/ÁMBITO: %s
                - INGRESOS: $%s
                - COSTOS: $%s
                - UTILIDAD: $%s
                - MARGEN: %s%%
                
                REGLAS DE FORMATO:
                1) NO uses corchetes [] ni asteriscos **.
                2) Usa un lenguaje profesional pero humano.
                3) Si los costos son $0, analiza si es por eficiencia extrema o por absorción central de costos.
                4) Destaca al equipo de repartidores: %s.
                5) Estructura: Mensaje de liderazgo -> Análisis de números -> Proyección operativa.
                """, 
                roleContext,
                (displayCity != null ? displayCity : "Nivel Nacional"),
                financials.totalRevenue(), 
                financials.operationalCosts(), 
                financials.netProfit(), 
                financials.profitMarginPercentage(),
                ranking.stream().map(com.routeoptimizer.dto.DriverRankingDTO::driverName).collect(Collectors.joining(", ")));
            
            String aiAnalysis = contextualAdvisor.askGeminiDirect(aiPrompt);
            XWPFParagraph aiPara = document.createParagraph();
            aiPara.setSpacingBefore(200);
            aiPara.setAlignment(ParagraphAlignment.BOTH);
            
            // Procesamos el texto para limpiar Markdown y respetar saltos de línea
            addCleanTextWithNewlines(aiPara, aiAnalysis);
            
            XWPFParagraph rankTitle = document.createParagraph();
            XWPFRun rankTitleRun = rankTitle.createRun();
            rankTitleRun.setBold(true);
            rankTitleRun.setFontSize(14);
            rankTitleRun.setText("3. RANKING DE EFICIENCIA LOCAL");
            
            XWPFTable rankTable = document.createTable();
            rankTable.setWidth("100%");
            XWPFTableRow hr = rankTable.getRow(0);
            hr.getCell(0).setText("Repartidor");
            hr.addNewTableCell().setText("Entregas");
            hr.addNewTableCell().setText("% Efectividad");
            hr.addNewTableCell().setText("Desempeño");
            
            for (var dr : ranking) {
                XWPFTableRow row = rankTable.createRow();
                row.getCell(0).setText(dr.driverName());
                row.getCell(1).setText(String.valueOf(dr.successfulDeliveries()));
                row.getCell(2).setText(dr.effectivenessPercentage() + "%");
                row.getCell(3).setText(dr.tag());
            }

            document.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando reporte semanal con IA: " + e.getMessage());
        }
    }

    public byte[] generateTransactionReport(String id) {
        // Implementación simplificada para el ejemplo
        return ("Reporte PDF Placeholder para ID: " + id).getBytes();
    }

    public byte[] generateGlobalReport() {
        // 1. AUTO-DETECCIÓN DE SEGURIDAD PARA FILTRADO REGIONAL
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String targetCity = null;
        if (auth != null && auth.getPrincipal() instanceof com.routeoptimizer.model.entity.User user) {
            if (user.getRole() == com.routeoptimizer.model.enums.Role.LOGISTICS) {
                targetCity = user.getAssignedCity();
            }
        }

        // 2. Filtrado de Datos por Ciudad
        final String finalCity = targetCity;
        java.util.List<Order> allOrders = orderRepository.findAll();
        java.util.List<Order> filteredOrders;
        
        if (finalCity != null && !finalCity.trim().isEmpty()) {
            filteredOrders = allOrders.stream()
                .filter(o -> o.getCity() != null && o.getCity().trim().equalsIgnoreCase(finalCity))
                .limit(100)
                .collect(Collectors.toList());
        } else {
            filteredOrders = allOrders.stream()
                .limit(100)
                .sorted(Comparator.comparing(Order::getCity, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
        }

        // 3. Ranking filtrado directamente por ciudad en DB
        java.util.List<com.routeoptimizer.dto.DriverRankingDTO> filteredRanking = analyticsService.getEfficiencyRanking(finalCity);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);

            document.open();

            // FUENTES PDF
            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, java.awt.Color.DARK_GRAY);
            com.lowagie.text.Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new java.awt.Color(31, 78, 120));
            com.lowagie.text.Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, java.awt.Color.WHITE);
            com.lowagie.text.Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, java.awt.Color.BLACK);

            // ENCABEZADO DINÁMICO
            String reportTitle = "PLANTILLA SEMANAL DE DESPACHO - " + (finalCity != null ? finalCity.toUpperCase() : "NACIONAL");
            Paragraph title = new Paragraph(reportTitle, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("VibeRoute Colombia - Gestión de Flota - Generado: " + new Date(), normalFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // --- SECCIÓN 1: DESEMPEÑO DE REPARTIDORES ---
            Paragraph rankTitle = new Paragraph("1. RANKING DE EFICIENCIA LOCAL", sectionFont);
            rankTitle.setSpacingAfter(10);
            document.add(rankTitle);

            PdfPTable rankTable = new PdfPTable(4);
            rankTable.setWidthPercentage(100);
            rankTable.setSpacingAfter(20);

            String[] rHeaders = {"Repartidor", "Entregas", "% Efectividad", "Desempeño"};
            for (String h : rHeaders) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new java.awt.Color(31, 78, 120));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                tableAddCellWithPadding(rankTable, cell);
            }

            for (var dr : filteredRanking) {
                rankTable.addCell(new Phrase(dr.driverName(), normalFont));
                rankTable.addCell(new Phrase(String.valueOf(dr.successfulDeliveries()), normalFont));
                rankTable.addCell(new Phrase(dr.effectivenessPercentage() + "%", normalFont));
                rankTable.addCell(new Phrase(dr.tag(), normalFont));
            }
            if (filteredRanking.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("No hay datos de conductores para esta región.", normalFont));
                empty.setColspan(4);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                rankTable.addCell(empty);
            }
            document.add(rankTable);

            // --- SECCIÓN 2: DETALLE DE PEDIDOS ---
            Paragraph ordersTitle = new Paragraph("2. LISTADO DE PEDIDOS DE LA SEMANA", sectionFont);
            ordersTitle.setSpacingAfter(10);
            document.add(ordersTitle);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 2f, 3f, 2f, 2f, 2f});

            // Headers
            String[] headers = {"ID", "Referencia", "Cliente", "Ciudad", "Estado", "Repartidor"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new java.awt.Color(64, 64, 64));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                tableAddCellWithPadding(table, cell);
            }

            // Datos
            for (Order o : filteredOrders) {
                table.addCell(new Phrase(String.valueOf(o.getId()), normalFont));
                table.addCell(new Phrase(o.getClientReference() != null ? o.getClientReference() : "-", normalFont));
                table.addCell(new Phrase(o.getClientName() != null ? o.getClientName() : "-", normalFont));
                table.addCell(new Phrase(o.getCity() != null ? o.getCity() : "-", normalFont));
                table.addCell(new Phrase(o.getStatus().toString(), normalFont));
                
                String driverName = "Sin Asignar";
                if (o.getBatchId() != null) {
                    try {
                        var batch = batchService.findById(o.getBatchId());
                        if (batch != null && batch.getDriver() != null) {
                            driverName = batch.getDriver().getName();
                        }
                    } catch (Exception e) {}
                }
                table.addCell(new Phrase(driverName, normalFont));
            }
            document.add(table);

            // SECCIÓN DE FIRMAS
            Paragraph footer = new Paragraph("\n\n__________________________          __________________________\n      Firma Coordinador                     Firma Transportador", normalFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.add(footer);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando Plantilla Semanal PDF: " + e.getMessage());
        }
    }

    private void tableAddCellWithPadding(PdfPTable table, PdfPCell cell) {
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addCleanTextWithNewlines(XWPFParagraph paragraph, String text) {
        if (text == null) return;
        
        // 1. Limpieza de Markdown común
        String cleanText = text.replaceAll("\\*\\*\\*", "")
                             .replaceAll("\\*\\*", "")
                             .replaceAll("###", "")
                             .replaceAll("---", "");

        // 2. Separar por líneas para insertar breaks reales de Word
        String[] lines = cleanText.split("\n");
        for (int i = 0; i < lines.length; i++) {
            XWPFRun run = paragraph.createRun();
            run.setItalic(true);
            run.setFontSize(11);
            run.setText(lines[i].trim());
            if (i < lines.length - 1) {
                run.addBreak();
            }
        }
    }

    public List<Map<String, String>> listDocuments() {
        return documentStore;
    }
}
