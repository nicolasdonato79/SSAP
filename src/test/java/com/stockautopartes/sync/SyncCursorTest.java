package com.stockautopartes.sync;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncCursorTest {

    @Test
    void sinCursorEsElInicio() {
        assertThat(SyncCursor.leer(null)).isEqualTo(SyncCursor.INICIO);
        assertThat(SyncCursor.leer(" ")).isEqualTo(SyncCursor.INICIO);
    }

    @Test
    void idaYVuelta() {
        UUID u = UUID.randomUUID();
        SyncCursor aMitad = new SyncCursor(1234, "a_item", u);
        assertThat(SyncCursor.leer(aMitad.toString())).isEqualTo(aMitad);
        assertThat(SyncCursor.leer("987")).isEqualTo(new SyncCursor(987, null, null));
        assertThat(SyncCursor.leer("987").aMitad()).isFalse();
    }

    @Test
    void cursorInvalido() {
        assertThatThrownBy(() -> SyncCursor.leer("abc")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyncCursor.leer("-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyncCursor.leer("1.a_item")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SyncCursor.leer("1.a_item.no-es-uuid")).isInstanceOf(IllegalArgumentException.class);
    }
}
