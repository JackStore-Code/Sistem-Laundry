import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.Properties;

/* =========================================================================
 * 1. PENERAPAN 4 KONSEP OOP (PBO)
 * ========================================================================= */

// [A] ABSTRACTION: Kelas abstrak sebagai cetak biru (blueprint) layanan
abstract class LayananLaundry {
    protected double jumlah; // [D] ENCAPSULATION: atribut dilindungi (protected)

    public LayananLaundry(double jumlah) {
        this.jumlah = (jumlah <= 0) ? 1 : jumlah;
    }

    // Method abstrak yang wajib diisi oleh kelas anak
    public abstract double tarifSatuan();

    // Perhitungan umum total harga
    public double hitungTotal() {
        return jumlah * tarifSatuan();
    }
}

// [B] INHERITANCE: Kelas anak mewarisi LayananLaundry
class LaundryKiloan extends LayananLaundry {
    private final double TARIF = 7000; // [D] ENCAPSULATION: data tarif disembunyikan (private)

    public LaundryKiloan(double kg) {
        super(kg);
    }

    // [C] POLYMORPHISM: Menentukan tarif sendiri untuk kiloan
    @Override
    public double tarifSatuan() {
        return TARIF;
    }
}

// [B] INHERITANCE: Kelas anak mewarisi LayananLaundry
class LaundrySatuan extends LayananLaundry {
    private final String item; // [D] ENCAPSULATION: data item disembunyikan (private)

    public LaundrySatuan(String item, double jumlah) {
        super(jumlah);
        this.item = item;
    }

    // [C] POLYMORPHISM: Menentukan tarif berbeda-beda sesuai jenis item
    @Override
    public double tarifSatuan() {
        if ("Sepatu".equals(item)) return 25000;
        if ("Jaket".equals(item))  return 15000;
        if ("Karpet".equals(item)) return 20000;
        return 10000;
    }
}

/* =========================================================================
 * 2. KONEKSI DATABASE (SQLite)
 * ========================================================================= */
class DB {
    private static java.sql.Driver driver;

    // Membuka database SQLite secara otomatis dari file .jar di folder
    static Connection getKoneksi() throws Exception {
        if (driver == null) {
            File[] fileJar = new File(".").listFiles((dir, nama) -> nama.endsWith(".jar") && nama.contains("sqlite"));
            if (fileJar != null && fileJar.length > 0) {
                URLClassLoader loader = new URLClassLoader(new URL[]{fileJar[0].toURI().toURL()}, DB.class.getClassLoader());
                driver = (java.sql.Driver) Class.forName("org.sqlite.JDBC", true, loader).getDeclaredConstructor().newInstance();
            }
        }
        return driver.connect("jdbc:sqlite:db_laundry.db", new Properties());
    }

    // Menyiapkan tabel pesanan
    static void buatTabel() {
        try (Connection c = getKoneksi(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS pesanan (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, nama TEXT, telepon TEXT, " +
                    "jenis TEXT, item TEXT, jumlah REAL, tarif REAL, total REAL, " +
                    "status TEXT DEFAULT 'Belum Diproses', tanggal TEXT)");
            s.executeUpdate("UPDATE pesanan SET status = 'Proses' WHERE status IN ('Cuci', 'Setrika')");
        } catch (Exception ignored) { }
    }
}

/* =========================================================================
 * 3. TAMPILAN APLIKASI (GUI SWING)
 * ========================================================================= */
public class LaundryApp extends JFrame {
    // Komponen Form Input
    private final JTextField txtNama = new JTextField();
    private final JTextField txtTelepon = new JTextField();
    private final JTextField txtJumlah = new JTextField("1");
    private final JComboBox<String> cmbJenis = new JComboBox<>(new String[]{"Kiloan", "Satuan"});
    private final JComboBox<String> cmbItem = new JComboBox<>(new String[]{"Pakaian"});
    
    // Status di atas hanya penampil (read-only)
    private final JLabel lblStatus = new JLabel("Belum Diproses");
    private final JLabel lblTarif = new JLabel("Rp 0");
    private final JLabel lblTotal = new JLabel("Rp 0");

    // Tabel Data
    private final String[] kolomTabel = {"ID", "Nama", "Telepon", "Jenis", "Item", "Jumlah", "Tarif (Rp)", "Total (Rp)", "Status", "Tanggal"};
    private final DefaultTableModel model = new DefaultTableModel(kolomTabel, 0) {
        @Override
        public boolean isCellEditable(int row, int col) {
            return col == 8; // Hanya kolom Status (indeks 8) yang bisa diubah langsung
        }
    };
    private final JTable tabel = new JTable(model);

    private int idDipilih = -1;
    private double totalHarga = 0;
    private boolean sedangMuat = false;

    public LaundryApp() {
        setTitle("Sistem Kasir Laundry Sederhana");
        setSize(950, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Judul atas
        JLabel lblJudul = new JLabel("SISTEM PENGELOLAAN LAUNDRY", SwingConstants.CENTER);
        lblJudul.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(lblJudul, BorderLayout.NORTH);

        // --- Panel Form Isian (Menggunakan GridLayout yang rapi & mudah dipahami) ---
        JPanel pForm = new JPanel(new GridLayout(4, 4, 10, 8));
        pForm.setBorder(BorderFactory.createTitledBorder("Form Pesanan"));
        
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblStatus.setForeground(Color.BLUE);

        pForm.add(new JLabel("Nama Pelanggan:"));    pForm.add(txtNama);
        pForm.add(new JLabel("Jenis Layanan:"));     pForm.add(cmbJenis);

        pForm.add(new JLabel("No. Telepon:"));       pForm.add(txtTelepon);
        pForm.add(new JLabel("Pilihan Item:"));      pForm.add(cmbItem);

        pForm.add(new JLabel("Berat (kg)/Jumlah:")); pForm.add(txtJumlah);
        pForm.add(new JLabel("Harga Satuan:"));      pForm.add(lblTarif);

        pForm.add(new JLabel("Status (Info):"));     pForm.add(lblStatus);
        pForm.add(new JLabel("Total Estimasi:"));    pForm.add(lblTotal);

        // --- Panel Tombol ---
        JPanel pTombol = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        JButton btnTambah = new JButton("Tambah Pesanan");
        JButton btnHapus = new JButton("Hapus Data");
        JButton btnBersih = new JButton("Bersihkan Form");
        JButton btnRefresh = new JButton("Muat Ulang");

        btnTambah.addActionListener(e -> tambahData());
        btnHapus.addActionListener(e -> hapusData());
        btnBersih.addActionListener(e -> resetForm());
        btnRefresh.addActionListener(e -> muatTabel());

        pTombol.add(btnTambah);
        pTombol.add(btnHapus);
        pTombol.add(btnBersih);
        pTombol.add(btnRefresh);

        // Gabungkan Form dan Tombol di bagian atas
        JPanel pAtas = new JPanel(new BorderLayout(5, 5));
        pAtas.add(pForm, BorderLayout.CENTER);
        pAtas.add(pTombol, BorderLayout.SOUTH);
        add(pAtas, BorderLayout.NORTH);

        // --- Bagian Tabel ---
        tabel.setRowHeight(24);
        
        // Dropdown status langsung pada kolom Status tabel
        JComboBox<String> cmbStatusTabel = new JComboBox<>(new String[]{"Belum Diproses", "Proses", "Selesai"});
        DefaultCellEditor editorStatus = new DefaultCellEditor(cmbStatusTabel);
        editorStatus.setClickCountToStart(1); // 1 kali klik langsung muncul pilihan status
        tabel.getColumnModel().getColumn(8).setCellEditor(editorStatus);

        add(new JScrollPane(tabel), BorderLayout.CENTER);

        // Event: Saat pilihan status di tabel diganti, langsung update database
        model.addTableModelListener(e -> {
            if (!sedangMuat && e.getColumn() == 8 && e.getType() == TableModelEvent.UPDATE) {
                int row = e.getFirstRow();
                int id = ambilIdDariTabel(row);
                String status = model.getValueAt(row, 8).toString();
                ubahStatus(id, status);
                if (id == idDipilih) lblStatus.setText(status);
            }
        });

        // Event: Hitung otomatis saat ganti jenis atau isi berat/jumlah
        cmbJenis.addActionListener(e -> {
            cmbItem.removeAllItems();
            if ("Kiloan".equals(cmbJenis.getSelectedItem())) {
                cmbItem.addItem("Pakaian");
            } else {
                cmbItem.addItem("Sepatu");
                cmbItem.addItem("Jaket");
                cmbItem.addItem("Karpet");
            }
            hitung();
        });
        cmbItem.addActionListener(e -> hitung());
        txtJumlah.addCaretListener(e -> hitung());

        // Event: Klik baris tabel untuk melihat detail di form
        tabel.getSelectionModel().addListSelectionListener(e -> {
            int baris = tabel.getSelectedRow();
            if (!e.getValueIsAdjusting() && baris >= 0) {
                idDipilih = ambilIdDariTabel(baris);
                txtNama.setText(tabel.getValueAt(baris, 1).toString());
                txtTelepon.setText(tabel.getValueAt(baris, 2).toString());
                cmbJenis.setSelectedItem(tabel.getValueAt(baris, 3).toString());
                cmbItem.setSelectedItem(tabel.getValueAt(baris, 4).toString());
                txtJumlah.setText(tabel.getValueAt(baris, 5).toString());
                lblStatus.setText(tabel.getValueAt(baris, 8).toString());
                hitung();
            }
        });

        // Jalankan database dan muat tabel pertama kali
        DB.buatTabel();
        hitung();
        muatTabel();
    }

    // Mengambil angka ID asli dari format LND-001
    private int ambilIdDariTabel(int baris) {
        String idTeks = model.getValueAt(baris, 0).toString();
        return Integer.parseInt(idTeks.replaceAll("\\D+", ""));
    }

    // Polimorfisme: Membuat objek Layanan sesuai pilihan user
    private LayananLaundry buatLayanan() {
        double jml;
        try { jml = Double.parseDouble(txtJumlah.getText().trim()); }
        catch (Exception e) { jml = 1; }

        if ("Kiloan".equals(cmbJenis.getSelectedItem())) {
            return new LaundryKiloan(jml);
        } else {
            return new LaundrySatuan(cmbItem.getSelectedItem().toString(), jml);
        }
    }

    // Menghitung harga secara otomatis (angka real bulat tanpa koma/desimal)
    private void hitung() {
        try {
            LayananLaundry layanan = buatLayanan();
            totalHarga = layanan.hitungTotal();
            lblTarif.setText("Rp " + (long) layanan.tarifSatuan());
            lblTotal.setText("Rp " + (long) totalHarga);
        } catch (Exception ignored) { }
    }

    // Menambah data pesanan baru
    private void tambahData() {
        if (txtNama.getText().trim().isEmpty() || txtTelepon.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nama dan No Telepon wajib diisi!");
            return;
        }

        LayananLaundry layanan = buatLayanan();
        long tarifBulat = (long) layanan.tarifSatuan();
        long totalBulat = (long) totalHarga;

        String sql = "INSERT INTO pesanan (nama, telepon, jenis, item, jumlah, tarif, total, status, tanggal) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, 'Belum Diproses', CURRENT_DATE)";

        try (Connection c = DB.getKoneksi(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, txtNama.getText().trim());
            p.setString(2, txtTelepon.getText().trim());
            p.setString(3, cmbJenis.getSelectedItem().toString());
            p.setString(4, cmbItem.getSelectedItem().toString());
            p.setDouble(5, layanan.jumlah);
            p.setLong(6, tarifBulat);
            p.setLong(7, totalBulat);
            p.executeUpdate();

            muatTabel();
            resetForm();
            JOptionPane.showMessageDialog(this, "Pesanan berhasil ditambahkan!");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Gagal menambah data: " + e.getMessage());
        }
    }

    // Mengubah status langsung di database
    private void ubahStatus(int id, String status) {
        String sql = "UPDATE pesanan SET status = ? WHERE id = ?";
        try (Connection c = DB.getKoneksi(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, status);
            p.setInt(2, id);
            p.executeUpdate();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Gagal ubah status: " + e.getMessage());
        }
    }

    // Menghapus data terpilih
    private void hapusData() {
        if (idDipilih < 0) {
            JOptionPane.showMessageDialog(this, "Pilih baris pada tabel yang ingin dihapus!");
            return;
        }
        String idFormatted = String.format("LND-%03d", idDipilih);
        int konfirmasi = JOptionPane.showConfirmDialog(this, "Hapus pesanan ID " + idFormatted + "?", "Konfirmasi", JOptionPane.YES_NO_OPTION);
        if (konfirmasi != JOptionPane.YES_OPTION) return;

        try (Connection c = DB.getKoneksi(); PreparedStatement p = c.prepareStatement("DELETE FROM pesanan WHERE id = ?")) {
            p.setInt(1, idDipilih);
            p.executeUpdate();
            muatTabel();
            resetForm();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Gagal menghapus: " + e.getMessage());
        }
    }

    // Membaca data dari SQLite ke tabel GUI
    private void muatTabel() {
        sedangMuat = true;
        try {
            model.setRowCount(0);
            try (Connection c = DB.getKoneksi(); Statement s = c.createStatement();
                 ResultSet r = s.executeQuery("SELECT * FROM pesanan ORDER BY id DESC")) {
                while (r.next()) {
                    int id = r.getInt("id");
                    String idFormat = String.format("LND-%03d", id); // Format ID: LND-001, LND-002, dst.

                    // Angka real bulat tanpa koma/desimal
                    long tarif = Math.round(r.getDouble("tarif"));
                    long total = Math.round(r.getDouble("total"));

                    // Tampilkan jumlah: jika bulat tampilkan bulat (misal 2 bukan 2.0)
                    double jml = r.getDouble("jumlah");
                    String jmlTeks = (jml == (long) jml) ? String.valueOf((long) jml) : String.valueOf(jml);

                    model.addRow(new Object[]{
                        idFormat,
                        r.getString("nama"),
                        r.getString("telepon"),
                        r.getString("jenis"),
                        r.getString("item"),
                        jmlTeks,
                        String.valueOf(tarif),
                        String.valueOf(total),
                        r.getString("status"),
                        r.getString("tanggal")
                    });
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Gagal memuat tabel: " + e.getMessage());
        } finally {
            sedangMuat = false;
        }
    }

    // Mengosongkan form ke kondisi awal
    private void resetForm() {
        idDipilih = -1;
        tabel.clearSelection();
        txtNama.setText("");
        txtTelepon.setText("");
        cmbJenis.setSelectedItem("Kiloan");
        txtJumlah.setText("1");
        lblStatus.setText("Belum Diproses");
        txtNama.requestFocus();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LaundryApp().setVisible(true));
    }
}
