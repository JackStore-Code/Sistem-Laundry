"""
ganti_teks.py - Ganti teks GUI dengan aman (otomatis backup + cek kompilasi).

Pakai:
    python alat/ganti_teks.py "Teks Lama" "Teks Baru"            -> lihat dulu (dry-run)
    python alat/ganti_teks.py "Teks Lama" "Teks Baru" --terapkan -> benar-benar ganti

Alur aman:
  1. Hitung berapa kali teks muncul
  2. Cek risiko (apakah dipakai logika/database)
  3. Backup LaundryApp.java -> LaundryApp.java.bak
  4. Ganti, lalu kompilasi. Kalau GAGAL -> otomatis kembalikan dari backup.
"""

import io
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUMBER = os.path.join(ROOT, 'LaundryApp.java')
CADANGAN = SUMBER + '.bak'

TANDA_LOGIKA = [
    (re.compile(r'\.equals\s*\('), 'dipakai logika .equals()'),
    (re.compile(r'SELECT|INSERT|UPDATE|DELETE'), 'masuk perintah SQL / database'),
    (re.compile(r'getStatus|setStatus|getJenis|setJenis'), 'dipakai sebagai nilai status/jenis'),
]


def main():
    arg = [a for a in sys.argv[1:] if a != '--terapkan']
    terapkan = '--terapkan' in sys.argv
    if len(arg) < 2:
        print(__doc__)
        return
    lama, baru = arg[0], arg[1]

    with io.open(SUMBER, encoding='utf-8') as f:
        isi = f.read()

    target = '"%s"' % lama
    jumlah = isi.count(target)
    print('Teks lama : "%s"' % lama)
    print('Teks baru : "%s"' % baru)
    print('Ditemukan : %d kemunculan (persis, termasuk tanda kutip)\n' % jumlah)

    if jumlah == 0:
        print('Tidak ada yang diganti. Cek ejaan/huruf besar-kecilnya.')
        return

    # cek risiko per baris
    bahaya = []
    for no, baris in enumerate(isi.splitlines(), 1):
        if target in baris:
            for pola, ket in TANDA_LOGIKA:
                if pola.search(baris):
                    bahaya.append((no, baris.strip()[:100], ket))
                    break

    if bahaya:
        print('!! PERHATIAN: teks ini juga dipakai untuk logika/database:')
        for no, baris, ket in bahaya:
            print('   baris %d: %s   (%s)' % (no, baris, ket))
        print('   Mengganti semua kemunculan biasanya AMAN karena semuanya ikut berubah,')
        print('   TAPI data lama di db_laundry.db tetap berisi teks lama.\n')

    if not terapkan:
        print('[DRY-RUN] Belum ada yang diubah.')
        print('Kalau sudah yakin, jalankan lagi dengan tambahan --terapkan')
        return

    # backup
    shutil.copy2(SUMBER, CADANGAN)
    print('Backup dibuat: %s' % os.path.basename(CADANGAN))

    isi_baru = isi.replace(target, '"%s"' % baru)
    with io.open(SUMBER, 'w', encoding='utf-8', newline='') as f:
        f.write(isi_baru)
    print('Teks diganti: %d tempat.' % jumlah)

    # verifikasi kompilasi (di folder sementara, supaya project tetap bersih)
    print('Mengompilasi untuk memastikan tidak rusak...')
    folder_uji = tempfile.mkdtemp(prefix='cek_laundry_')
    hasil = subprocess.run(
        ['javac', '-encoding', 'UTF-8', '-d', folder_uji, SUMBER],
        capture_output=True, text=True, cwd=ROOT)
    shutil.rmtree(folder_uji, ignore_errors=True)

    if hasil.returncode == 0:
        print('KOMPILASI BERHASIL. Perubahan aman.')
        print('Bandingkan dengan: git diff LaundryApp.java')
        print('Kalau mau batalkan  : copy LaundryApp.java.bak LaundryApp.java')
    else:
        print('KOMPILASI GAGAL -> mengembalikan berkas asli dari backup.')
        print(hasil.stderr[:1500])
        shutil.copy2(CADANGAN, SUMBER)
        print('LaundryApp.java sudah dikembalikan seperti semula.')


if __name__ == '__main__':
    main()
