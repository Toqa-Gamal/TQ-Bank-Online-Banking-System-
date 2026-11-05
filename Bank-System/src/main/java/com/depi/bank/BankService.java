package com.depi.bank;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

@Service
public class BankService {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern NAME = Pattern.compile("^[A-Za-z ]{3,50}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^(010|011|012|015)\\d{8}$");
    private static final Pattern PASSWORD =
            Pattern.compile("^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[!@#$%^&*]).{8,}$");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder enc;
    private final SessionStore sessions;

    public BankService(JdbcTemplate jdbc, PasswordEncoder enc, SessionStore sessions) {
        this.jdbc = jdbc;
        this.enc = enc;
        this.sessions = sessions;
    }

    // ---------------------------------------------------------------- helpers
    private ApiException bad(String m) { return new ApiException(400, m); }

    private static String nz(String s) { return s == null ? "" : s; }

    private static BigDecimal dec(Object o) { return o == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(o)); }

    private static long num(Object o) { return ((Number) o).longValue(); }

    private String now() { return LocalDateTime.now().format(TS); }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private Map<String, Object> find(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM accounts WHERE id=?", id);
        if (rows.isEmpty()) throw new ApiException(404, "Account not found.");
        return rows.get(0);
    }

    private Map<String, Object> owned(long userId, long accId) {
        Map<String, Object> acc = find(accId);
        if (num(acc.get("user_id")) != userId) throw new ApiException(403, "Access denied.");
        return acc;
    }

    private void checkActive(Map<String, Object> acc) {
        if ("FROZEN".equals(acc.get("status"))) {
            throw new ApiException(422, "Account is frozen. Transactions are not allowed.");
        }
    }

    private BigDecimal strict(BigDecimal a) {
        if (a == null || a.stripTrailingZeros().scale() > 2) throw bad("Invalid amount format.");
        if (a.signum() <= 0) throw bad("Amount must be greater than zero.");
        return a.setScale(2, RoundingMode.HALF_UP);
    }

    private String newRef() {
        String chars = "ABCDEF0123456789";
        while (true) {
            StringBuilder sb = new StringBuilder("#TX-");
            for (int i = 0; i < 5; i++) sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
            if (count("SELECT COUNT(*) FROM transactions WHERE reference_id=?", sb.toString()) == 0) return sb.toString();
        }
    }

    private void tx(String ref, long accId, String type, BigDecimal amt, BigDecimal balAfter, String cp, String note) {
        jdbc.update("INSERT INTO transactions(reference_id,account_id,type,amount,balance_after,counterparty_account,note,created_at) VALUES(?,?,?,?,?,?,?,?)",
                ref, accId, type, amt.toPlainString(), balAfter.toPlainString(), cp, note, now());
    }

    private void setBalance(long accId, BigDecimal bal) {
        jdbc.update("UPDATE accounts SET balance=? WHERE id=?", bal.toPlainString(), accId);
    }

    private BigDecimal dailyOut(long accId) {
        Double d = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM transactions WHERE account_id=? AND type='WITHDRAWAL' AND date(created_at)=?",
                Double.class, accId, LocalDate.now().toString());
        return BigDecimal.valueOf(d == null ? 0 : d);
    }

    private Map<String, Object> receipt(String ref, String type, Object accNo, BigDecimal amt, BigDecimal balAfter) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("referenceId", ref);
        m.put("type", type);
        m.put("accountNumber", accNo);
        m.put("amount", amt);
        m.put("balanceAfter", balAfter);
        m.put("dateTime", now());
        return m;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw bad("Invalid date format. Use YYYY-MM-DD.");
        }
    }

    // ---------------------------------------------------------------- auth
    public void register(Dto.Register r) {
        String name = nz(r.fullName()).trim();
        if (!NAME.matcher(name).matches()) throw bad("Full name must be 3-50 alphabetic characters.");
        String email = nz(r.email()).trim();
        if (!EMAIL.matcher(email).matches()) throw bad("Invalid email format.");
        String phone = nz(r.phone()).trim();
        if (!PHONE.matcher(phone).matches()) throw bad("Phone must be 11 digits starting with 010, 011, 012 or 015.");
        LocalDate dob;
        try {
            dob = LocalDate.parse(nz(r.dateOfBirth()).trim());
        } catch (DateTimeParseException e) {
            throw bad("Invalid date of birth. Use YYYY-MM-DD.");
        }
        if (!dob.isBefore(LocalDate.now())) throw bad("Date of birth must be in the past.");
        if (Period.between(dob, LocalDate.now()).getYears() <= 18) throw bad("You must be at least 18 years old.");
        String pw = nz(r.password());
        if (!PASSWORD.matcher(pw).matches()) {
            throw bad("Password must be 8-20 chars with uppercase, lowercase, digit and special character (!@#$%^&*).");
        }
        if (!pw.equals(r.confirmPassword())) throw bad("Passwords do not match.");
        if (count("SELECT COUNT(*) FROM users WHERE LOWER(email)=LOWER(?)", email) > 0)
            throw new ApiException(409, "Email already registered.");
        if (count("SELECT COUNT(*) FROM users WHERE phone=?", phone) > 0)
            throw new ApiException(409, "Phone number already registered.");
        jdbc.update("INSERT INTO users(full_name,email,phone,date_of_birth,password_hash,created_at) VALUES(?,?,?,?,?,?)",
                name, email, phone, dob.toString(), enc.encode(pw), now());
    }

    public Map<String, Object> login(Dto.Login r) {
        String id = nz(r.identifier()).trim();
        String pw = nz(r.password());
        if (id.isEmpty() || pw.isEmpty()) throw bad("Email/phone and password are required.");
        List<Map<String, Object>> rows =
                jdbc.queryForList("SELECT * FROM users WHERE LOWER(email)=LOWER(?) OR phone=?", id, id);
        if (rows.isEmpty()) throw new ApiException(401, "Email or phone not registered.");
        Map<String, Object> u = rows.get(0);
        long uid = num(u.get("id"));
        String lockedUntil = (String) u.get("locked_until");
        if (lockedUntil != null && LocalDateTime.parse(lockedUntil).isAfter(LocalDateTime.now())) {
            throw new ApiException(423, "Account locked due to multiple failed attempts. Please try again in 15 minutes.");
        }
        if (!enc.matches(pw, String.valueOf(u.get("password_hash")))) {
            int f = (int) num(u.get("failed_attempts")) + 1;
            if (f > 3) {
                jdbc.update("UPDATE users SET failed_attempts=0, locked_until=? WHERE id=?",
                        LocalDateTime.now().plusMinutes(15).toString(), uid);
                throw new ApiException(423, "Account locked due to multiple failed attempts. Please try again in 15 minutes.");
            }
            jdbc.update("UPDATE users SET failed_attempts=? WHERE id=?", f, uid);
            throw new ApiException(401, "Invalid email/phone or password.");
        }
        jdbc.update("UPDATE users SET failed_attempts=0, locked_until=NULL WHERE id=?", uid);
        String role = String.valueOf(u.get("role"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", sessions.create(uid, role));
        out.put("userId", uid);
        out.put("fullName", u.get("full_name"));
        out.put("role", role);
        return out;
    }

    // ---------------------------------------------------------------- accounts
    @Transactional
    public Map<String, Object> openAccount(long userId, Dto.OpenAccount r) {
        String type = nz(r.accountType()).trim().toUpperCase();
        if (!type.equals("SAVINGS") && !type.equals("CURRENT")) throw bad("Account type must be SAVINGS or CURRENT.");
        BigDecimal bal = r.openingBalance();
        if (bal == null || bal.stripTrailingZeros().scale() > 2) throw bad("Invalid amount format.");
        if (bal.compareTo(new BigDecimal("100")) < 0) throw bad("Opening balance must be at least 100 EGP.");
        if (bal.compareTo(new BigDecimal("1000000")) > 0) throw bad("Opening balance cannot exceed 1,000,000 EGP.");
        if (count("SELECT COUNT(*) FROM accounts WHERE user_id=?", userId) > 5)
            throw bad("Maximum of 5 accounts reached.");
        String no;
        do {
            no = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        } while (count("SELECT COUNT(*) FROM accounts WHERE account_number=?", no) > 0);
        bal = bal.setScale(2, RoundingMode.HALF_UP);
        jdbc.update("INSERT INTO accounts(account_number,user_id,account_type,balance,status,created_at) VALUES(?,?,?,?,'ACTIVE',?)",
                no, userId, type, bal.toPlainString(), now());
        Long id = jdbc.queryForObject("SELECT id FROM accounts WHERE account_number=?", Long.class, no);
        String ref = newRef();
        tx(ref, id, "DEPOSIT", bal, bal, null, "Opening balance");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("accountNumber", no);
        out.put("accountType", type);
        out.put("balance", bal);
        out.put("status", "ACTIVE");
        out.put("referenceId", ref);
        return out;
    }

    public Map<String, Object> listAccounts(long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, account_number AS accountNumber, account_type AS accountType, balance, status, created_at AS createdAt FROM accounts WHERE user_id=? ORDER BY id",
                userId);
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> r : rows) total = total.add(dec(r.get("balance")));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accounts", rows);
        out.put("totalBalance", total);
        if (rows.isEmpty()) out.put("message", "You have no accounts yet. Open your first account.");
        return out;
    }

    public Map<String, Object> getAccount(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, account_number AS accountNumber, account_type AS accountType, balance, status, created_at AS createdAt FROM accounts WHERE id=?",
                id);
        if (rows.isEmpty()) throw new ApiException(404, "Account not found.");
        return rows.get(0);
    }

    // ---------------------------------------------------------------- transactions
    @Transactional
    public Map<String, Object> deposit(long userId, long accId, BigDecimal raw) {
        Map<String, Object> acc = owned(userId, accId);
        if (raw == null) throw bad("Invalid amount format.");
        BigDecimal amt = raw.setScale(2, RoundingMode.HALF_UP);
        if (amt.signum() <= 0) throw bad("Amount must be greater than zero.");
        if (amt.compareTo(new BigDecimal("50000")) >= 0)
            throw new ApiException(422, "Deposit amount cannot exceed 50,000 EGP per transaction.");
        checkActive(acc);
        BigDecimal newBal = dec(acc.get("balance")).add(amt);
        setBalance(accId, newBal);
        String ref = newRef();
        tx(ref, accId, "DEPOSIT", amt, newBal, null, null);
        return receipt(ref, "DEPOSIT", acc.get("account_number"), amt, newBal);
    }

    @Transactional
    public Map<String, Object> withdraw(long userId, long accId, BigDecimal raw) {
        Map<String, Object> acc = owned(userId, accId);
        BigDecimal amt = strict(raw);
        if (amt.compareTo(new BigDecimal("20000")) > 0)
            throw new ApiException(422, "Withdrawal amount cannot exceed 20,000 EGP per transaction.");
        checkActive(acc);
        if (dailyOut(accId).add(amt).compareTo(new BigDecimal("30000")) > 0)
            throw new ApiException(422, "Daily withdrawal limit of 30,000 EGP exceeded.");
        BigDecimal newBal = dec(acc.get("balance")).subtract(amt);
        boolean savings = "SAVINGS".equals(acc.get("account_type"));
        boolean insufficient = savings ? newBal.signum() <= 0 : newBal.compareTo(new BigDecimal("-1000")) < 0;
        if (insufficient) throw new ApiException(422, "Insufficient funds.");
        setBalance(accId, newBal);
        String ref = newRef();
        tx(ref, accId, "WITHDRAWAL", amt, newBal, null, null);
        return receipt(ref, "WITHDRAWAL", acc.get("account_number"), amt, newBal);
    }

    @Transactional
    public Map<String, Object> transfer(long userId, Dto.Transfer r) {
        if (r.fromAccountId() == null) throw bad("Source account is required.");
        Map<String, Object> src = owned(userId, r.fromAccountId());
        String toNo = nz(r.toAccountNumber()).trim();
        if (!toNo.matches("\\d{10}")) throw bad("Invalid account number.");
        BigDecimal amt = strict(r.amount());
        if (amt.compareTo(new BigDecimal("25000")) > 0)
            throw new ApiException(422, "Transfer amount cannot exceed 25,000 EGP per transaction.");
        String note = r.note();
        if (note != null && note.length() > 50) throw bad("Note cannot exceed 50 characters.");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM accounts WHERE account_number=?", toNo);
        if (rows.isEmpty()) throw new ApiException(404, "Destination account not found.");
        Map<String, Object> dst = rows.get(0);
        long srcId = num(src.get("id"));
        long dstId = num(dst.get("id"));
        if (srcId == dstId) throw bad("Source and destination accounts cannot be the same.");
        checkActive(src);
        if (dailyOut(srcId).add(amt).compareTo(new BigDecimal("30000")) > 0)
            throw new ApiException(422, "Daily withdrawal limit of 30,000 EGP exceeded.");
        BigDecimal floor = "CURRENT".equals(src.get("account_type")) ? new BigDecimal("-1000") : BigDecimal.ZERO;
        BigDecimal newSrc = dec(src.get("balance")).subtract(amt);
        if (newSrc.compareTo(floor) < 0) throw new ApiException(422, "Insufficient funds.");
        BigDecimal newDst = dec(dst.get("balance")).add(amt);
        setBalance(srcId, newSrc);
        setBalance(dstId, newDst);
        String ref = newRef();
        tx(ref, srcId, "TRANSFER_OUT", amt, newSrc, toNo, note);
        tx(ref, dstId, "TRANSFER_IN", amt, newDst, String.valueOf(src.get("account_number")), note);
        return receipt(ref, "TRANSFER", src.get("account_number"), amt, newSrc);
    }

    // ---------------------------------------------------------------- history
    public Map<String, Object> history(long userId, long accId, String type, String from, String to, int page) {
        owned(userId, accId);
        StringBuilder where = new StringBuilder(" WHERE account_id=?");
        List<Object> args = new ArrayList<>();
        args.add(accId);
        if (type != null && !type.isBlank() && !type.equalsIgnoreCase("ALL")) {
            String t = type.trim().toUpperCase();
            if (!List.of("DEPOSIT", "WITHDRAWAL", "TRANSFER_IN", "TRANSFER_OUT").contains(t))
                throw bad("Invalid transaction type.");
            where.append(" AND type=?");
            args.add(t);
        }
        LocalDate f = parseDate(from);
        LocalDate t2 = parseDate(to);
        LocalDate today = LocalDate.now();
        if ((f != null && f.isAfter(today)) || (t2 != null && t2.isAfter(today)))
            throw bad("Date cannot be in the future.");
        if (f != null && t2 != null && f.isAfter(t2)) throw bad("From date cannot be after To date.");
        if (f != null) {
            where.append(" AND date(created_at)>=?");
            args.add(f.toString());
        }
        if (t2 != null) {
            where.append(" AND date(created_at)<?");
            args.add(t2.toString());
        }
        if (page < 1) throw bad("Page must be 1 or greater.");
        int total = count("SELECT COUNT(*) FROM transactions" + where, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(10);
        pageArgs.add((page - 1) * 10);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT reference_id AS referenceId, type, amount, created_at AS createdAt, balance_after AS balanceAfter, counterparty_account AS counterpartyAccount, note FROM transactions"
                        + where + " ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?", pageArgs.toArray());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transactions", rows);
        out.put("page", page);
        out.put("totalRecords", total);
        out.put("totalPages", (total + 9) / 10);
        if (rows.isEmpty()) out.put("message", "No transactions found.");
        return out;
    }

    public String exportCsv(long userId, long accId) {
        owned(userId, accId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT reference_id, type, amount, created_at, balance_after FROM transactions WHERE account_id=? ORDER BY created_at DESC, id DESC",
                accId);
        StringBuilder sb = new StringBuilder("Reference ID,Type,Amount,Date/Time,Balance After\n");
        for (Map<String, Object> r : rows) {
            sb.append(r.get("reference_id")).append(',').append(r.get("type")).append(',')
              .append(r.get("amount")).append(',').append(r.get("created_at")).append(',')
              .append(r.get("balance_after")).append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- admin
    public List<Map<String, Object>> adminAccounts(String search) {
        String s = search == null ? "" : search.trim();
        return jdbc.queryForList(
                "SELECT a.id, a.account_number AS accountNumber, a.account_type AS accountType, a.balance, a.status, u.full_name AS owner FROM accounts a LEFT JOIN users u ON u.id=a.user_id WHERE a.account_number LIKE ? ORDER BY a.id",
                "%" + s + "%");
    }

    @Transactional
    public Map<String, Object> setFrozen(long adminId, long accId, String reason, boolean freeze) {
        String r = nz(reason).trim();
        if (r.length() < 5 || r.length() > 100) throw bad("Reason must be between 5 and 100 characters.");
        Map<String, Object> acc = find(accId);
        String status = String.valueOf(acc.get("status"));
        if (freeze && "FROZEN".equals(status)) throw new ApiException(409, "Account is already frozen.");
        if (!freeze && "ACTIVE".equals(status)) throw new ApiException(409, "Account is already active.");
        jdbc.update("UPDATE accounts SET status=? WHERE id=?", freeze ? "FROZEN" : "ACTIVE", accId);
        jdbc.update("INSERT INTO admin_audit_log(admin_id,account_id,action,reason,created_at) VALUES(?,?,?,?,?)",
                adminId, accId, freeze ? "FREEZE" : "UNFREEZE", r, now());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", freeze ? "Account frozen." : "Account unfrozen.");
        return out;
    }
}
