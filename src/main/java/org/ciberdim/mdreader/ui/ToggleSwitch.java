package org.ciberdim.mdreader.ui;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.css.PseudoClass;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * A custom toggle switch UI component.
 */
public class ToggleSwitch extends StackPane {

    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Rectangle background;
    private final Circle thumb;

    public ToggleSwitch() {
        getStyleClass().add("toggle-switch");

        background = new Rectangle(36, 20);
        background.setArcWidth(20);
        background.setArcHeight(20);
        background.getStyleClass().add("toggle-switch-background");

        thumb = new Circle(8);
        thumb.getStyleClass().add("toggle-switch-thumb");
        
        // Initial positioning
        thumb.setTranslateX(-8);

        getChildren().addAll(background, thumb);

        // Bind animation
        selected.addListener((obs, oldVal, newVal) -> {
            TranslateTransition tt = new TranslateTransition(Duration.millis(200), thumb);
            tt.setToX(newVal ? 8 : -8);
            tt.play();
            
            background.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, newVal);
        });

        setOnMouseClicked(e -> {
            selected.set(!selected.get());
        });
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        selected.set(value);
    }
}
