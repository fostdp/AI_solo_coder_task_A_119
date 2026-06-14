package com.metrology.balance.controller;

import com.metrology.balance.entity.Dynasty;
import com.metrology.balance.repository.DynastyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/dynasties")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DynastyController {

    private final DynastyRepository dynastyRepository;

    @GetMapping
    public ResponseEntity<?> getAllDynasties() {
        try {
            List<Dynasty> dynasties = dynastyRepository.findAll();
            return ResponseEntity.ok(dynasties);
        } catch (Exception e) {
            log.error("获取朝代列表失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDynastyById(@PathVariable Integer id) {
        try {
            return dynastyRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("获取朝代详情失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<?> getDynastyByName(@PathVariable String name) {
        try {
            return dynastyRepository.findByName(name)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("按名称获取朝代失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
