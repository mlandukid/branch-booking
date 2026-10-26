package com.loyiso.booking.branch;

import com.loyiso.booking.common.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public List<Branch> findAll() {
        return branchRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Branch getById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Branch %d not found".formatted(id)));
    }
}