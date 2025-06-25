package org.example.paintbrushfx;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXSlider;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * A JavaFX-based Paint application allowing users to draw shapes, freehand, erase, fill shapes,
 * and save/open images with undo/redo functionality.
 */
public class PaintBrush extends Application {
    private Canvas canvas;
    private GraphicsContext gc;
    private String currentTool = "FreeHand";
    private Color currentColor = Color.BLACK;
    private double startX, startY;
    private boolean isFilled = false;
    private Stack<WritableImage> undoStack = new Stack<>();
    private Stack<WritableImage> redoStack = new Stack<>();
    private WritableImage currentSnapshot;
    private List<Shape> shapes = new ArrayList<>();
    private List<Double> currentFreehandXPoints = new ArrayList<>();
    private List<Double> currentFreehandYPoints = new ArrayList<>();
    private static final boolean DEBUG = false; // Debug mode

    /**
     * Inner class representing a shape with properties like type, coordinates, color, and fill status.
     */
    private static class Shape {
        String type;
        double x, y, width, height;
        boolean filled;
        Color color;
        double[] xPoints;
        double[] yPoints;
        double lineStartX, lineStartY, lineEndX, lineEndY;
        List<Double> freehandXPoints;
        List<Double> freehandYPoints;
        double lineWidth;

        Shape(String type, double x, double y, double width, double height, boolean filled, Color color,
              double[] xPoints, double[] yPoints, double lineStartX, double lineStartY, double lineEndX, double lineEndY,
              List<Double> freehandXPoints, List<Double> freehandYPoints, double lineWidth) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.filled = filled;
            this.color = color;
            this.xPoints = xPoints != null ? xPoints.clone() : null;
            this.yPoints = yPoints != null ? yPoints.clone() : null;
            this.lineStartX = lineStartX;
            this.lineStartY = lineStartY;
            this.lineEndX = lineEndX;
            this.lineEndY = lineEndY;
            this.freehandXPoints = freehandXPoints != null ? new ArrayList<>(freehandXPoints) : null;
            this.freehandYPoints = freehandYPoints != null ? new ArrayList<>(freehandYPoints) : null;
            this.lineWidth = lineWidth;
        }

        boolean contains(double px, double py) {
            if (type.equals("Line") || type.equals("FreeHand") || type.equals("Eraser")) {
                return false;
            } else if (type.equals("Rectangle")) {
                return px >= x && px <= x + width && py >= y && py <= y + height;
            } else if (type.equals("Oval")) {
                double centerX = x + width / 2;
                double centerY = y + height / 2;
                double a = width / 2;
                double b = height / 2;
                double value = Math.pow(px - centerX, 2) / Math.pow(a, 2) + Math.pow(py - centerY, 2) / Math.pow(b, 2);
                return value <= 1;
            } else if (type.equals("Triangle")) {
                double area = Math.abs((xPoints[0] * (yPoints[1] - yPoints[2]) +
                        xPoints[1] * (yPoints[2] - yPoints[0]) +
                        xPoints[2] * (yPoints[0] - yPoints[1])) / 2);
                double area1 = Math.abs((px * (yPoints[1] - yPoints[2]) +
                        xPoints[1] * (yPoints[2] - py) +
                        xPoints[2] * (py - yPoints[1])) / 2);
                double area2 = Math.abs((xPoints[0] * (py - yPoints[2]) +
                        px * (yPoints[2] - yPoints[0]) +
                        xPoints[2] * (yPoints[0] - py)) / 2);
                double area3 = Math.abs((xPoints[0] * (yPoints[1] - py) +
                        xPoints[1] * (py - yPoints[0]) +
                        px * (yPoints[0] - yPoints[1])) / 2);
                return Math.abs(area - (area1 + area2 + area3)) <= 0.01;
            }
            return false;
        }
    }

    @Override
    public void start(Stage primaryStage) {
        canvas = new Canvas(900, 600);
        gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setStroke(currentColor);
        gc.setLineWidth(2);
        saveCanvas();

        canvas.widthProperty().addListener((obs, oldVal, newVal) -> resizeCanvas());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> resizeCanvas());

        VBox toolbar = new VBox(5);
        toolbar.setStyle("-fx-padding: 10; -fx-background-color: #f4f4f4;");
        HBox sections = new HBox(0);

        // File Section
        VBox fileBox = new VBox(5);
        fileBox.setAlignment(Pos.TOP_CENTER);
        fileBox.setPrefWidth(120);
        fileBox.setPrefHeight(150);
        fileBox.setStyle("-fx-padding: 0 10 0 10;");
        fileBox.getStyleClass().add("section-box");
        HBox fileButtons = new HBox(5);
        fileButtons.setAlignment(Pos.CENTER);

        JFXButton newButton = createStyledButton("New", "mdi2n-note-plus-outline");
        newButton.setOnAction(e -> {
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setStroke(currentColor);
            gc.setLineWidth(currentTool.equals("Eraser") ? gc.getLineWidth() : gc.getLineWidth());
            shapes.clear();
            saveCanvas();
        });

        JFXButton saveButton = createStyledButton("Save", "mdi2f-floppy");
        saveButton.setOnAction(e -> saveImage(primaryStage));

        JFXButton openButton = createStyledButton("Open", "mdi2f-folder-open");
        openButton.setOnAction(e -> openImage(primaryStage));

        fileButtons.getChildren().addAll(newButton, saveButton, openButton);
        Label fileLabel = new Label("File");
        fileLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");
        fileBox.getChildren().addAll(fileLabel, fileButtons);

        // Colors Section
        VBox colorsBox = new VBox(5);
        colorsBox.setAlignment(Pos.TOP_CENTER);
        colorsBox.setPrefWidth(120);
        colorsBox.setPrefHeight(150);
        colorsBox.setStyle("-fx-padding: 0 10 0 10;");
        colorsBox.getStyleClass().add("section-box");
        HBox basicColors = new HBox(5);
        basicColors.setAlignment(Pos.CENTER);

        Color[] colors = {
                Color.BLACK, Color.GRAY, Color.PURPLE, Color.BLUE,
                Color.GREEN, Color.ORANGERED, Color.RED, Color.YELLOW
        };

        for (int i = 0; i < colors.length; i++) {
            final int index = i;
            JFXButton colorButton = new JFXButton();
            colorButton.getStyleClass().add("color-square");
            colorButton.setStyle("-fx-background-color: #" + toHex(colors[i]) + ";");
            colorButton.setPrefSize(25, 25);
            colorButton.setMinSize(25, 25);
            colorButton.setMaxSize(25, 25);
            colorButton.setOnAction(e -> setColor(colors[index]));
            basicColors.getChildren().add(colorButton);
        }

        HBox colorPickerBox = new HBox(5);
        colorPickerBox.setAlignment(Pos.CENTER);
        Label editColorsLabel = new Label("Edit Colors");
        editColorsLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #333;");
        ColorPicker colorPicker = new ColorPicker();
        colorPicker.setValue(currentColor);
        colorPicker.setOnAction(e -> setColor(colorPicker.getValue()));
        colorPickerBox.getChildren().addAll(editColorsLabel, colorPicker);
        Label colorsLabel = new Label("Colors");
        colorsLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");
        colorsBox.getChildren().addAll(colorsLabel, basicColors, colorPickerBox);

        List<JFXButton> toolButtons = new ArrayList<>();

        // Shapes Section
        VBox shapesBox = new VBox(5);
        shapesBox.setAlignment(Pos.TOP_CENTER);
        shapesBox.setPrefWidth(120);
        shapesBox.setPrefHeight(150);
        shapesBox.setStyle("-fx-padding: 0 10 0 10;");
        shapesBox.getStyleClass().add("section-box");
        HBox shapesButtons = new HBox(5);
        shapesButtons.setAlignment(Pos.CENTER);

        JFXButton lineButton = createStyledButton("Line", "mdi2v-vector-line");
        lineButton.setOnAction(e -> {
            setActiveButton(lineButton, toolButtons);
            currentTool = "Line";
            gc.setLineWidth(gc.getLineWidth());
        });

        JFXButton rectangleButton = createStyledButton("Rectangle", "mdi2s-square-outline");
        rectangleButton.setOnAction(e -> {
            setActiveButton(rectangleButton, toolButtons);
            currentTool = "Rectangle";
            gc.setLineWidth(gc.getLineWidth());
        });

        JFXButton ovalButton = createStyledButton("Oval", "mdi2c-circle-outline");
        ovalButton.setOnAction(e -> {
            setActiveButton(ovalButton, toolButtons);
            currentTool = "Oval";
            gc.setLineWidth(gc.getLineWidth());
        });

        JFXButton triangleButton = createStyledButton("Triangle", "mdi2v-vector-triangle");
        triangleButton.setOnAction(e -> {
            setActiveButton(triangleButton, toolButtons);
            currentTool = "Triangle";
            gc.setLineWidth(gc.getLineWidth());
        });

        shapesButtons.getChildren().addAll(lineButton, rectangleButton, ovalButton, triangleButton);
        toolButtons.addAll(List.of(lineButton, rectangleButton, ovalButton, triangleButton));

        HBox brushSizeBox = new HBox(5);
        brushSizeBox.setAlignment(Pos.CENTER);
        Label brushSizeLabel = new Label("Brush Size");
        brushSizeLabel.setStyle("-fx-font-size: 12; -fx-text-fill: #333;");
        JFXSlider strokeSlider = new JFXSlider(1, 20, 2);
        strokeSlider.setPrefWidth(80);
        strokeSlider.valueProperty().addListener((obs, old, newVal) -> {
            double lineWidth = newVal.doubleValue();
            gc.setLineWidth(currentTool.equals("Eraser") ? lineWidth * 2 : lineWidth);
        });
        brushSizeBox.getChildren().addAll(brushSizeLabel, strokeSlider);
        JFXCheckBox filledCheckBox = new JFXCheckBox("Filled Shapes");
        filledCheckBox.setStyle("-fx-font-size: 12; -fx-text-fill: #333;");
        filledCheckBox.setOnAction(e -> isFilled = filledCheckBox.isSelected());
        Label shapesLabel = new Label("Shapes");
        shapesLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");
        shapesBox.getChildren().addAll(shapesLabel, shapesButtons, brushSizeBox, filledCheckBox);

        // Tools Section
        VBox toolsBox = new VBox(5);
        toolsBox.setAlignment(Pos.TOP_CENTER);
        toolsBox.setPrefWidth(120);
        toolsBox.setPrefHeight(150);
        toolsBox.setStyle("-fx-padding: 0 10 0 10;");
        toolsBox.getStyleClass().add("section-box");
        HBox toolsButtonsRow1 = new HBox(5);
        toolsButtonsRow1.setAlignment(Pos.CENTER);
        HBox toolsButtonsRow2 = new HBox(5);
        toolsButtonsRow2.setAlignment(Pos.CENTER);

        JFXButton freeHandButton = createStyledButton("Free Hand", "mdi2p-pencil");
        freeHandButton.setOnAction(e -> {
            setActiveButton(freeHandButton, toolButtons);
            currentTool = "FreeHand";
            gc.setStroke(currentColor);
            gc.setLineWidth(strokeSlider.getValue());
        });

        JFXButton eraserButton = createStyledButton("Eraser", "mdi2e-eraser");
        eraserButton.setOnAction(e -> {
            setActiveButton(eraserButton, toolButtons);
            currentTool = "Eraser";
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(strokeSlider.getValue() * 2);
        });

        JFXButton fillShapeButton = createStyledButton("Fill Shape", "mdi2b-bucket");
        fillShapeButton.setOnAction(e -> {
            setActiveButton(fillShapeButton, toolButtons);
            currentTool = "FillShape";
        });

        JFXButton undoButton = createStyledButton("Undo", "mdi2u-undo");
        undoButton.setOnAction(e -> undo());

        JFXButton redoButton = createStyledButton("Redo", "mdi2r-redo");
        redoButton.setOnAction(e -> redo());

        JFXButton clearButton = createStyledButton("Clear All", "mdi2t-trash-can-outline");
        clearButton.setOnAction(e -> {
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setStroke(currentColor);
            gc.setLineWidth(currentTool.equals("Eraser") ? strokeSlider.getValue() * 2 : strokeSlider.getValue());
            shapes.clear();
            saveCanvas();
        });

        toolsButtonsRow1.getChildren().addAll(freeHandButton, eraserButton, fillShapeButton);
        toolsButtonsRow2.getChildren().addAll(undoButton, redoButton, clearButton);
        toolButtons.addAll(List.of(freeHandButton, eraserButton, fillShapeButton));
        Label toolsLabel = new Label("Tools");
        toolsLabel.setStyle("-fx-font-size: 10; -fx-text-fill: #666;");
        toolsBox.getChildren().addAll(toolsLabel, toolsButtonsRow1, toolsButtonsRow2);

        sections.getChildren().addAll(fileBox, colorsBox, shapesBox, toolsBox);
        toolbar.getChildren().add(sections);

        // Dynamic Label for hover (follows cursor)
        Label hoverLabel = new Label("");
        hoverLabel.getStyleClass().add("hover-label");
        hoverLabel.setVisible(false);

        // Setup hover labels for buttons
        setupHoverLabel(newButton, hoverLabel, "New Canvas (Ctrl+N)");
        setupHoverLabel(saveButton, hoverLabel, "Save Image (Ctrl+S)");
        setupHoverLabel(openButton, hoverLabel, "Open Image (Ctrl+O)");
        for (int i = 0; i < basicColors.getChildren().size(); i++) {
            JFXButton colorButton = (JFXButton) basicColors.getChildren().get(i);
            setupHoverLabel(colorButton, hoverLabel, colors[i].toString().substring(2, 8).toUpperCase());
        }
        setupHoverLabel(lineButton, hoverLabel, "Draw Line");
        setupHoverLabel(rectangleButton, hoverLabel, "Draw Rectangle");
        setupHoverLabel(ovalButton, hoverLabel, "Draw Oval");
        setupHoverLabel(triangleButton, hoverLabel, "Draw Triangle");
        setupHoverLabel(freeHandButton, hoverLabel, "Freehand Drawing");
        setupHoverLabel(eraserButton, hoverLabel, "Erase Content");
        setupHoverLabel(fillShapeButton, hoverLabel, "Fill Shape with Color");
        setupHoverLabel(undoButton, hoverLabel, "Undo (Ctrl+Z)");
        setupHoverLabel(redoButton, hoverLabel, "Redo (Ctrl+Y)");
        setupHoverLabel(clearButton, hoverLabel, "Clear Canvas");

        canvas.setOnMousePressed(e -> {
            double[] coords = clampCoordinates(e.getX(), e.getY());
            startX = coords[0];
            startY = coords[1];
            if (currentTool.equals("FreeHand") || currentTool.equals("Eraser")) {
                currentFreehandXPoints.clear();
                currentFreehandYPoints.clear();
                currentFreehandXPoints.add(startX);
                currentFreehandYPoints.add(startY);
            } else if (currentTool.equals("FillShape")) {
                debug("Clicked at: (" + startX + ", " + startY + ")");
                fillShapeAtPoint(startX, startY);
            }
        });

        canvas.setOnMouseDragged(e -> {
            double[] coords = clampCoordinates(e.getX(), e.getY());
            double currentX = coords[0];
            double currentY = coords[1];
            if (currentTool.equals("FreeHand") || currentTool.equals("Eraser")) {
                if (currentFreehandXPoints.isEmpty() ||
                        Math.hypot(currentX - currentFreehandXPoints.get(currentFreehandXPoints.size() - 1),
                                currentY - currentFreehandYPoints.get(currentFreehandYPoints.size() - 1)) > 2) {
                    currentFreehandXPoints.add(currentX);
                    currentFreehandYPoints.add(currentY);
                    Shape tempShape = new Shape(
                            currentTool,
                            startX, startY, 0, 0,
                            false,
                            currentTool.equals("Eraser") ? Color.WHITE : currentColor,
                            null, null,
                            0, 0, 0, 0,
                            new ArrayList<>(currentFreehandXPoints),
                            new ArrayList<>(currentFreehandYPoints),
                            gc.getLineWidth()
                    );
                    redrawCanvas();
                    drawSingleShape(gc, tempShape);
                }
            } else if (!currentTool.equals("FillShape")) {
                redrawCanvas();
                drawShape(gc, currentTool, startX, startY, currentX, currentY, true);
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (!currentTool.equals("FillShape")) {
                double[] coords = clampCoordinates(e.getX(), e.getY());
                double endX = coords[0];
                double endY = coords[1];
                if (undoStack.isEmpty() && shapes.size() > 0) {
                    debug("Clearing shapes list after drawing new shape on empty canvas");
                    shapes.clear();
                }
                if (currentTool.equals("FreeHand") || currentTool.equals("Eraser")) {
                    Shape shape = new Shape(
                            currentTool,
                            startX, startY, 0, 0,
                            false,
                            currentTool.equals("Eraser") ? Color.WHITE : currentColor,
                            null, null,
                            0, 0, 0, 0,
                            new ArrayList<>(currentFreehandXPoints),
                            new ArrayList<>(currentFreehandYPoints),
                            gc.getLineWidth()
                    );
                    redrawCanvas();
                    drawSingleShape(gc, shape);
                    shapes.add(shape);
                } else {
                    drawShape(gc, currentTool, startX, startY, endX, endY, false);
                }
                saveCanvas();
            }
        });

        // Setup main layout with hover label
        VBox root = new VBox(0, toolbar, canvas);
        root.getChildren().add(hoverLabel); // Add hover label to root
        root.setFillWidth(true);
        root.setStyle("-fx-background-color: transparent;");
        canvas.widthProperty().bind(primaryStage.widthProperty());
        canvas.heightProperty().bind(primaryStage.heightProperty().subtract(toolbar.heightProperty()));
        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());

        scene.setOnKeyPressed(e -> {
            if (e.isControlDown()) {
                if (e.getCode() == KeyCode.Z) {
                    undo();
                    e.consume();
                } else if (e.getCode() == KeyCode.Y) {
                    redo();
                    e.consume();
                } else if (e.getCode() == KeyCode.S) {
                    saveImage(primaryStage);
                    e.consume();
                } else if (e.getCode() == KeyCode.O) {
                    openImage(primaryStage);
                    e.consume();
                } else if (e.getCode() == KeyCode.N) {
                    gc.setFill(Color.WHITE);
                    gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                    gc.setStroke(currentColor);
                    gc.setLineWidth(currentTool.equals("Eraser") ? strokeSlider.getValue() * 2 : strokeSlider.getValue());
                    shapes.clear();
                    saveCanvas();
                    e.consume();
                }
            }
        });

        primaryStage.setScene(scene);
        primaryStage.setTitle("Paint Brush");
        primaryStage.setResizable(true);
        primaryStage.setWidth(950);
        primaryStage.setHeight(700);
        primaryStage.show();
    }

    private JFXButton createStyledButton(String text, String iconCode) {
        JFXButton button = new JFXButton(text);
        button.setPrefSize(90, 30);
        button.setMinSize(90, 30);
        button.setMaxSize(90, 30);
        button.setStyle("-fx-text-fill: #333; -fx-font-size: 12;");
        button.setRipplerFill(Color.WHITE.deriveColor(0, 1, 1, 0.4));
        if (iconCode != null) {
            FontIcon icon = new FontIcon(iconCode);
            icon.setIconSize(16);
            icon.getStyleClass().add("font-icon");
            button.setGraphic(icon);
            button.setGraphicTextGap(5);
        }
        return button;
    }

    private void setupHoverLabel(JFXButton button, Label hoverLabel, String text) {
        button.setOnMouseEntered(e -> {
            hoverLabel.setText(text);
            hoverLabel.setVisible(true);
            updateHoverLabelPosition(hoverLabel, e);
        });
        button.setOnMouseMoved(e -> updateHoverLabelPosition(hoverLabel, e));
        button.setOnMouseExited(e -> hoverLabel.setVisible(false));
    }

    private void updateHoverLabelPosition(Label hoverLabel, MouseEvent e) {
        // Position label next to cursor (offset by 10 pixels right and 5 pixels down)
        hoverLabel.setLayoutX(e.getSceneX() + 10);
        hoverLabel.setLayoutY(e.getSceneY() + 5);
    }

    private String toHex(Color color) {
        return String.format("%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    private void setColor(Color color) {
        currentColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), 1.0);
        gc.setStroke(currentColor);
        currentTool = currentTool.equals("Eraser") ? "FreeHand" : currentTool;
        debug("Color set to: " + currentColor);
    }

    private void setActiveButton(JFXButton activeButton, List<JFXButton> buttons) {
        buttons.forEach(button -> button.getStyleClass().remove("active-button"));
        activeButton.getStyleClass().add("active-button");
    }

    private void setupGraphicsContext(GraphicsContext gc, Color strokeColor, Color fillColor, double lineWidth) {
        gc.save();
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.setStroke(strokeColor);
        gc.setFill(fillColor);
        gc.setLineWidth(lineWidth);
        gc.setGlobalAlpha(1.0);
    }

    private void drawSingleShape(GraphicsContext gc, Shape shape) {
        setupGraphicsContext(gc, shape.color, shape.color, shape.lineWidth);
        double x = shape.x;
        double y = shape.y;
        double width = shape.width;
        double height = shape.height;

        switch (shape.type) {
            case "Line":
                gc.strokeLine(shape.lineStartX, shape.lineStartY, shape.lineEndX, shape.lineEndY);
                break;
            case "Rectangle":
                if (shape.filled) {
                    gc.fillRect(x, y, width, height);
                }
                gc.strokeRect(x, y, width, height);
                break;
            case "Oval":
                if (shape.filled) {
                    gc.fillOval(x, y, width, height);
                }
                gc.strokeOval(x, y, width, height);
                break;
            case "Triangle":
                if (shape.filled) {
                    gc.fillPolygon(shape.xPoints, shape.yPoints, 3);
                }
                gc.strokePolygon(shape.xPoints, shape.yPoints, 3);
                break;
            case "FreeHand":
            case "Eraser":
                if (!shape.freehandXPoints.isEmpty()) {
                    gc.beginPath();
                    gc.moveTo(shape.freehandXPoints.get(0), shape.freehandYPoints.get(0));
                    for (int i = 1; i < shape.freehandXPoints.size(); i++) {
                        gc.lineTo(shape.freehandXPoints.get(i), shape.freehandYPoints.get(i));
                    }
                    gc.stroke();
                }
                break;
        }
        gc.restore();
    }

    private void drawShape(GraphicsContext gc, String tool, double startX, double startY, double endX, double endY, boolean isPreview) {
        double x = Math.min(startX, endX);
        double y = Math.min(startY, endY);
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);
        Shape shape = null;

        switch (tool) {
            case "Line":
                shape = new Shape("Line", x, y, width, height, false, currentColor, null, null,
                        startX, startY, endX, endY, null, null, gc.getLineWidth());
                break;
            case "Rectangle":
                shape = new Shape("Rectangle", x, y, width, height, isFilled, currentColor, null, null,
                        0, 0, 0, 0, null, null, gc.getLineWidth());
                break;
            case "Oval":
                shape = new Shape("Oval", x, y, width, height, isFilled, currentColor, null, null,
                        0, 0, 0, 0, null, null, gc.getLineWidth());
                break;
            case "Triangle":
                double leftX = Math.min(startX, endX);
                double rightX = Math.max(startX, endX);
                double topX = (leftX + rightX) / 2;
                double topY = Math.min(startY, endY);
                double baseY = Math.max(startY, endY);
                double[] xPoints = {leftX, rightX, topX};
                double[] yPoints = {baseY, baseY, topY};

                for (int i = 0; i < xPoints.length; i++) {
                    xPoints[i] = Math.max(0, Math.min(xPoints[i], canvas.getWidth()));
                    yPoints[i] = Math.max(0, Math.min(yPoints[i], canvas.getHeight()));
                }

                shape = new Shape("Triangle", x, y, width, height, isFilled, currentColor, xPoints, yPoints,
                        0, 0, 0, 0, null, null, gc.getLineWidth());
                break;
        }

        if (shape != null) {
            drawSingleShape(gc, shape);
            if (!isPreview) {
                shapes.add(shape);
            }
        }
    }

    private void fillShapeAtPoint(double x, double y) {
        boolean shapeFound = false;
        for (int i = shapes.size() - 1; i >= 0; i--) {
            Shape shape = shapes.get(i);
            if (shape.contains(x, y)) {
                shapeFound = true;
                shape.filled = true;
                shape.color = new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 1.0);
                debug("Filled shape: " + shape.type + " at (" + shape.x + ", " + shape.y + ")");
                redrawCanvas();
                drawSingleShape(gc, shape);
                saveCanvas();
                break;
            }
        }
        if (!shapeFound) {
            debug("No shape found at (" + x + ", " + y + ")");
        }
    }

    private void redrawCanvas() {
        gc.save();
        gc.setTransform(1, 0, 0, 1, 0, 0);
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        for (Shape shape : shapes) {
            drawSingleShape(gc, shape);
        }
        gc.restore();
    }

    private void saveCanvas() {
        redoStack.clear();
        WritableImage snapshot = canvas.snapshot(null, null);
        undoStack.push(snapshot);
        currentSnapshot = snapshot;
    }

    private void restoreCanvasState() {
        if (!undoStack.isEmpty()) {
            currentSnapshot = undoStack.peek();
            gc.setTransform(1, 0, 0, 1, 0, 0);
            gc.drawImage(currentSnapshot, 0, 0);
        }
    }

    private void undo() {
        if (!undoStack.isEmpty()) {
            redoStack.push(undoStack.pop());
            if (undoStack.isEmpty()) {
                gc.setFill(Color.WHITE);
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                shapes.clear();
                currentSnapshot = null;
            } else {
                restoreCanvasState();
            }
        }
    }

    private void redo() {
        if (!redoStack.isEmpty()) {
            WritableImage snapshot = redoStack.pop();
            undoStack.push(snapshot);
            gc.setTransform(1, 0, 0, 1, 0, 0);
            gc.drawImage(snapshot, 0, 0);
        }
    }

    private void saveImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG Files", "*.png"));
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                WritableImage image = canvas.snapshot(null, null);
                BufferedImage bufferedImage = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int x = 0; x < image.getWidth(); x++) {
                    for (int y = 0; y < image.getHeight(); y++) {
                        int argb = image.getPixelReader().getArgb(x, y);
                        bufferedImage.setRGB(x, y, argb);
                    }
                }
                if (!file.getName().toLowerCase().endsWith(".png")) {
                    file = new File(file.getPath() + ".png");
                }
                ImageIO.write(bufferedImage, "png", file);
                showAlert(Alert.AlertType.INFORMATION, "Success", "Image saved successfully!");
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to save image: " + e.getMessage());
            }
        }
    }

    private void openImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg"));
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                Image image = new Image(file.toURI().toString(), canvas.getWidth(), canvas.getHeight(), false, true);
                gc.drawImage(image, 0, 0);
                shapes.clear();
                saveCanvas();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Image loaded successfully!");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to load image: " + e.getMessage());
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void debug(String message) {
        if (DEBUG) {
            System.out.println(message);
        }
    }

    private void resizeCanvas() {
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
        restoreCanvasState();
    }

    private double[] clampCoordinates(double x, double y) {
        return new double[] {
                Math.max(0, Math.min(x, canvas.getWidth())),
                Math.max(0, Math.min(y, canvas.getHeight()))
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}