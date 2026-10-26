package com.loyiso.booking.branch;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.loyiso.booking.common.GlobalExceptionHandler;
import com.loyiso.booking.common.ResourceNotFoundException;
import com.loyiso.booking.slot.SlotResponse;
import com.loyiso.booking.slot.SlotService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BranchControllerTest {

    private BranchService branchService;
    private SlotService slotService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        branchService = mock(BranchService.class);
        slotService = mock(SlotService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new BranchController(branchService, slotService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listsBranches() throws Exception {
        Branch branch = new Branch("Sandton", "Sandton City", "Johannesburg",
                LocalTime.of(8, 0), LocalTime.of(16, 0), 30);
        when(branchService.findAll()).thenReturn(List.of(branch));

        mockMvc.perform(get("/api/v1/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sandton"))
                .andExpect(jsonPath("$[0].slotMinutes").value(30));
    }

    @Test
    void unknownBranchReturnsProblemDetail404() throws Exception {
        when(branchService.getById(99L)).thenThrow(new ResourceNotFoundException("Branch 99 not found"));

        mockMvc.perform(get("/api/v1/branches/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Branch 99 not found"));
    }

    @Test
    void returnsSlotsForADate() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 2);
        when(slotService.findSlots(1L, date))
                .thenReturn(List.of(new SlotResponse(LocalTime.of(8, 0), LocalTime.of(8, 30), true)));

        mockMvc.perform(get("/api/v1/branches/1/slots").param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].available").value(true));
    }
}