package com.depi.bank;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BankController {
    private final BankService svc;

    public BankController(BankService svc) {
        this.svc = svc;
    }

    private static Map<String, Object> msg(String m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", m);
        return out;
    }

    private static void admin(String role) {
        if (!"ADMIN".equals(role)) throw new ApiException(403, "Access denied.");
    }

    // ---- auth
    @PostMapping("/auth/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Dto.Register r) {
        svc.register(r);
        return ResponseEntity.status(201).body(msg("Registration successful."));
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody Dto.Login r) {
        return svc.login(r);
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout() {
        return msg("Logged out successfully.");
    }

    // ---- accounts
    @PostMapping("/accounts")
    public ResponseEntity<Map<String, Object>> open(@RequestAttribute("userId") Long uid, @RequestBody Dto.OpenAccount r) {
        return ResponseEntity.status(201).body(svc.openAccount(uid, r));
    }

    @GetMapping("/accounts")
    public Map<String, Object> list(@RequestAttribute("userId") Long uid) {
        return svc.listAccounts(uid);
    }

    @GetMapping("/accounts/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return svc.getAccount(id);
    }

    // ---- transactions
    @PostMapping("/accounts/{id}/deposit")
    public Map<String, Object> deposit(@RequestAttribute("userId") Long uid, @PathVariable long id, @RequestBody Dto.Amount b) {
        return svc.deposit(uid, id, b.amount());
    }

    @PostMapping("/accounts/{id}/withdraw")
    public Map<String, Object> withdraw(@RequestAttribute("userId") Long uid, @PathVariable long id, @RequestBody Dto.Amount b) {
        return svc.withdraw(uid, id, b.amount());
    }

    @PostMapping("/transfers")
    public Map<String, Object> transfer(@RequestAttribute("userId") Long uid, @RequestBody Dto.Transfer b) {
        return svc.transfer(uid, b);
    }

    // ---- history
    @GetMapping("/accounts/{id}/transactions")
    public Map<String, Object> history(@RequestAttribute("userId") Long uid, @PathVariable long id,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(defaultValue = "1") int page) {
        return svc.history(uid, id, type, from, to, page);
    }

    @GetMapping("/accounts/{id}/transactions/export")
    public ResponseEntity<String> export(@RequestAttribute("userId") Long uid, @PathVariable long id,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions.csv")
                .body(svc.exportCsv(uid, id));
    }

    // ---- admin
    @GetMapping("/admin/accounts")
    public List<Map<String, Object>> adminAccounts(@RequestAttribute("role") String role,
                                                   @RequestParam(required = false) String search) {
        admin(role);
        return svc.adminAccounts(search);
    }

    @PutMapping("/admin/accounts/{id}/freeze")
    public Map<String, Object> freeze(@RequestAttribute("userId") Long uid, @PathVariable long id, @RequestBody Dto.Reason b) {
        return svc.setFrozen(uid, id, b.reason(), true);
    }

    @PutMapping("/admin/accounts/{id}/unfreeze")
    public Map<String, Object> unfreeze(@RequestAttribute("userId") Long uid, @RequestAttribute("role") String role,
                                        @PathVariable long id, @RequestBody Dto.Reason b) {
        admin(role);
        return svc.setFrozen(uid, id, b.reason(), false);
    }
}
