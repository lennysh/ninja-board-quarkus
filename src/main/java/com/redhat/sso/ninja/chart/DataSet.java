package com.redhat.sso.ninja.chart;

import java.util.ArrayList;
import java.util.List;

public class DataSet {
    private String fillColor;
    private String strokeColor;
    private List<Integer> data;
    private Integer borderWidth;
    private List<String> backgroundColor;
    private List<String> borderColor;
    private String label;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getFillColor() {
        return fillColor;
    }

    public void setFillColor(String fillColor) {
        this.fillColor = fillColor;
    }

    public String getStrokeColor() {
        return strokeColor;
    }

    public void setStrokeColor(String strokeColor) {
        this.strokeColor = strokeColor;
    }

    public List<Integer> getData() {
        if (data == null) data = new ArrayList<>();
        return data;
    }

    public void setData(List<Integer> data) {
        this.data = data;
    }

    public Integer getBorderWidth() {
        return borderWidth;
    }

    public void setBorderWidth(Integer borderWidth) {
        this.borderWidth = borderWidth;
    }

    public List<String> getBackgroundColor() {
        if (backgroundColor == null) backgroundColor = new ArrayList<>();
        return backgroundColor;
    }

    public void setBackgroundColor(List<String> backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public List<String> getBorderColor() {
        if (borderColor == null) borderColor = new ArrayList<>();
        return borderColor;
    }

    public void setBorderColor(List<String> borderColor) {
        this.borderColor = borderColor;
    }
}

