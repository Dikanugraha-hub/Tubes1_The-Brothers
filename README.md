# Laporan Tugas Besar 1: Pemanfaatan Algoritma Greedy untuk Bot Battlecode 2025

**Mata Kuliah:** IF2211 Strategi Algoritma - Semester II Tahun Ajaran 2025/2026

## Anggota Tim

**Tim GitHub:** `the_Brothers`

1. **Fazri** Arrashyi Putra (NIM: 13524127)
2. **Dika** Pramudya Nugraha (NIM: 13524132)
3. **Dzakwan** Muhammad Khairan Putra Purnama (NIM: 13524145)
---

## Deskripsi Proyek

Proyek ini merupakan pengembangan tiga bot strategi untuk permainan **Battlecode 2025**, sebuah kompetisi *Real-Time Strategy* (RTS) berbasis kecerdasan buatan. Bot dirancang untuk beroperasi secara otonom di peta dua dimensi dengan tujuan utama **menguasai lebih dari 70% wilayah peta** atau **menghancurkan seluruh unit lawan**.

Fokus utama pengembangan adalah implementasi dan analisis pendekatan **Algoritma *Greedy***, yang krusial untuk pengambilan keputusan cepat (*local optimum*) dalam keterbatasan sumber daya komputasi (batas *bytecode*/waktu) pada setiap giliran permainan.

## Bot yang Dikembangkan

Kami mengembangkan tiga varian bot dengan fokus heuristik yang berbeda, di mana setiap bot mengimplementasikan fungsi seleksi *greedy* yang unik:

### 1. Bot Utama (Hybrid Strategy)

Bot utama menerapkan strategi yang seimbang antara **ekspansi wilayah** dan **penguatan ekonomi**. Pendekatan ini menggabungkan pewarnaan *tile*, pembangunan menara dari *ruin*, dan pengendalian area.

| Strategi Greedy Kunci | Fungsi Obyektif |
| :---: | :--- |
| **Pemilihan Target Pembangunan Menara** | Meminimalkan jarak tempuh ke *ruin* terdekat. |
| **Pemilihan Target Pewarnaan Tile** | Memaksimalkan pertambahan wilayah sekutu per aksi *paint*. |
| **Pemilihan Arah Gerak Eksplorasi** | Memaksimalkan potensi ekspansi wilayah pada langkah berikutnya. |
| **Mekanisme Anti-Stuck** | Menjaga kelancaran eksplorasi wilayah dengan mengubah target gerak jika terdeteksi pola pergerakan berulang. |

### 2. Bot Alternatif 1 (Area Dominance Specialist)

Bot ini dirancang dengan fokus absolut pada **maksimasi penguasaan wilayah** secepat mungkin. Prioritas utama bot adalah melakukan aksi pewarnaan (*paint*) pada setiap giliran.

| Strategi Greedy Kunci | Fungsi Obyektif |
| :---: | :--- |
| **Pemilihan Target Pewarnaan Tile** | Memaksimalkan dampak ekspansi (skor heuristik tertinggi pada *tile* kosong/musuh, memperhitungkan *frontier expansion*). |
| **Pemilihan Arah Gerak Ekspansi** | Memaksimalkan potensi pewarnaan *tile* di giliran selanjutnya (*look-ahead* satu langkah). |

### 3. Bot Alternatif 2 (Resource Specialist)

Bot ini berfokus pada **maksimasi keuntungan ekonomi dan sumber daya (*chips*)** tim melalui dua aksi strategis.

| Strategi Greedy Kunci | Fungsi Obyektif |
| :---: | :--- |
| **Pencarian dan Pembentukan SRP** | Meminimalkan jarak dan waktu tempuh menuju *ruins* untuk mengaktifkan *Special Resource Pattern* (SRP) guna mempercepat produksi *chips*. |
| **Pemilihan Prioritas Upgrade Tower** | Memaksimalkan utilitas menara sekutu terkuat (memilih menara dengan *Health Points* tertinggi untuk di-*upgrade*). |

---

## Instalasi dan Penggunaan

1. **Prasyarat:** Pastikan Anda memiliki Java Development Kit (JDK) terinstal pada sistem Anda.
2. **Klona Repositori:** Klon repositori ini ke mesin lokal Anda.
3. **Kompilasi Bot:** Kompilasi kode bot Java Anda menggunakan *tool* yang disediakan oleh platform Battlecode.
4. **Jalankan Pertandingan:** Unggah
