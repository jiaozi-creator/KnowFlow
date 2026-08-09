package com.knowflow.document;

import java.util.List;

public record ParsedDocument(List<Section> sections) {
    public record Section(Integer pageNumber, String heading, String text) {}
}
