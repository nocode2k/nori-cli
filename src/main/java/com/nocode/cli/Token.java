package com.nocode.cli;

import java.util.List;
public class Token {
    private String surface;
    private List<String> attrs;

    public Token(String surface, List<String> attrs) {
        this.surface = surface;
        this.attrs = attrs;
    }

    public String getSurface() {
        return surface;
    }

    public void setSurface(String surface) {
        this.surface = surface;
    }

    public List<String> getAttrs() {
        return attrs;
    }

    public void setAttrs(List<String> attrs) {
        this.attrs = attrs;
    }

    public boolean equals(Object obj) {
        Token token = (Token) obj;
        boolean status = false;

        if (this.getSurface().equals(token.getSurface())
                && this.getAttrs().equals(token.getAttrs())) {
            status = true;
        }

        return status;
    }
}
