import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.PriorityQueue;

public class Main {

    // ---------- Gerador de aleatórios (LCG período grande) ----------
    // Seed sugerida: 15 (pode mudar se quiser reprodutibilidade via outro seed)
    private static int previous = 1037;
    private static double nextRandom() {
        // LCG estável para 100k amostras
        int a = 61;
        int c = 37;
        int M = 365_789;
        previous = ((a * previous) + c) % M;
        return (double) previous / M; // [0,1)
    }

    // Acumula tempo nos estados atuais de F1 e F2
    private static void acumulaTempo(double dt, int n1, int n2,
                                     double[] timeInState1, double[] timeInState2) {
        timeInState1[n1] += dt;
        timeInState2[n2] += dt;
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.US);

        // --------- “Orçamento” de aleatórios ----------
        final int COUNT = 100_000;
        double[] lista = new double[COUNT];
        for (int i = 0; i < COUNT; i++) lista[i] = nextRandom();
        final int[] pos = {0}; // próximo índice a consumir

        // (Opcional) Salva aleatórios em arquivo TXT simples (um por linha)
        try (PrintWriter out = new PrintWriter(new FileWriter("aleatorios.txt"))) {
            for (double v : lista) out.println(v);
        }
        System.out.println("Arquivo 'aleatorios.txt' criado com " + COUNT + " números.");

        // --------- Helpers ----------
        java.util.function.BiFunction<Double, Double, Double> uniform = (a, b) -> {
            if (pos[0] >= COUNT) throw new RuntimeException("Sem mais números pseudoaleatórios.");
            double u = lista[pos[0]++];           // consome 1 número
            return a + (b - a) * u;               // U(a,b)
        };

        // --------- Parâmetros das filas, para alterar os valores mude aqui) ----------
        // Fila 1: G/G/2/3  | Chegadas U(1,4) | Serviço U(3,4)
        final int SERVERS1 = 2;
        final int CAP1 = 3;                // K total da F1 = 3 (2 em serviço + 1 em fila máx)
        final double ARR1_A = 1.0, ARR1_B = 4.0;
        final double SVC1_A = 3.0, SVC1_B = 4.0;

        // Fila 2: G/G/1/5  | Serviço U(2,3) | (sem chegadas externas)
        final int SERVERS2 = 1;
        final int CAP2 = 5;                // K total da F2 = 5 (1 em serviço + 4 em fila máx)
        final double SVC2_A = 2.0, SVC2_B = 3.0;//tempo minimo e maximo de chegada na fila 2 como no simulador
        //______________________________________________________________________________________________
        // Tempo de trânsito entre filas (0 = passagem imediata)
        final double TRANSIT = 0.0;

        // --------- Estado global ----------
        double t = 0.0;                     // relógio global (min)
        int n1 = 0, n2 = 0;                 // ocupação total em cada fila

        // Escalonador
        double nextArr1 = 2.0;              // PRIMEIRA CHEGADA FIXA em 2.0 (não sorteia)
        PriorityQueue<Double> dep1 = new PriorityQueue<>();  // términos em F1
        PriorityQueue<Double> pass = new PriorityQueue<>();  // passagens F1->F2 (dep1 + TRANSIT)
        PriorityQueue<Double> dep2 = new PriorityQueue<>();  // términos em F2

        // Métricas
        int losses1 = 0, losses2 = 0;
        double[] timeInState1 = new double[CAP1 + 1];
        double[] timeInState2 = new double[CAP2 + 1];

        // --------- Loop principal ----------
        while (pos[0] < COUNT) {
            double nextDep1 = dep1.isEmpty() ? Double.POSITIVE_INFINITY : dep1.peek();
            double nextPass = pass.isEmpty() ? Double.POSITIVE_INFINITY : pass.peek();
            double nextDep2 = dep2.isEmpty() ? Double.POSITIVE_INFINITY : dep2.peek();

            // Ordem de prioridade de eventos em empate (determinística):
            // 1) Chegada F1, 2) Passagem F1->F2, 3) Saída F1, 4) Saída F2
            // (A linha abaixo pega o mínimo; os if's a seguir definem a prioridade por ordem)
            double tNext = Math.min(Math.min(nextArr1, nextPass), Math.min(nextDep1, nextDep2));
            if (Double.isInfinite(tNext)) break;

            // Blindagem de limites
            n1 = Math.max(0, Math.min(n1, CAP1));
            n2 = Math.max(0, Math.min(n2, CAP2));

            // Acumula tempo nos estados atuais
            acumulaTempo(tNext - t, n1, n2, timeInState1, timeInState2);
            t = tNext;

            if (tNext == nextArr1) {
                // ================= CHEGADA em F1 =================
                if (n1 < CAP1) {
                    int busyBefore = Math.min(n1, SERVERS1);
                    n1++; // entra em F1
                    // Inicia serviço se abriu servidor
                    if (Math.min(n1, SERVERS1) > busyBefore) {
                        dep1.add(t + uniform.apply(SVC1_A, SVC1_B));
                    }
                } else {
                    losses1++; // perda por capacidade cheia em F1
                }
                // Próxima chegada EXTERNA só para F1
                nextArr1 = t + uniform.apply(ARR1_A, ARR1_B);

            } else if (tNext == nextPass) {
                // ================= PASSAGEM F1 -> F2 =================
                pass.poll();

                // Sai de F1
                if (n1 > 0) n1--;
                // Se ainda tem gente esperando em F1 (n1 >= SERVERS1), inicia novo serviço
                if (n1 >= SERVERS1) {
                    dep1.add(t + uniform.apply(SVC1_A, SVC1_B));
                }

                // Tenta entrar em F2
                if (n2 < CAP2) {
                    int busyBefore2 = Math.min(n2, SERVERS2);
                    n2++; // entra em F2
                    // Inicia serviço em F2 se abriu servidor
                    if (Math.min(n2, SERVERS2) > busyBefore2) {
                        dep2.add(t + uniform.apply(SVC2_A, SVC2_B));
                    }
                } else {
                    losses2++; // perda em F2 (bloqueio por capacidade total)
                }

            } else if (tNext == nextDep1) {
                // =============== TÉRMINO DE SERVIÇO em F1 ===============
                dep1.poll();
                // Saída efetiva de F1 ocorre via PASSAGEM (aplica transit)
                pass.add(t + TRANSIT);

            } else {
                // ================= SAÍDA em F2 =================
                dep2.poll();
                if (n2 > 0) n2--; // sai do sistema na F2
                // Se ainda há fila em F2 (n2 >= SERVERS2), inicia novo serviço
                if (n2 >= SERVERS2) {
                    dep2.add(t + uniform.apply(SVC2_A, SVC2_B));
                }
            }
        }

        // ---------- Relatórios ----------
        double totalMin = t;
        double totalSec = totalMin * 60.0;

        System.out.println("========== TANDEM ==========");
        System.out.printf("Fila 1: G/G/%d/%d | Chegadas U(%.1f,%.1f) | Serviço U(%.1f,%.1f)%n",
                SERVERS1, CAP1, ARR1_A, ARR1_B, SVC1_A, SVC1_B);
        System.out.printf("Fila 2: G/G/%d/%d | Serviço U(%.1f,%.1f)%n",
                SERVERS2, CAP2, SVC2_A, SVC2_B);
        System.out.printf("Transit: %.1f  | Tempo total: %.2f s (%.4f min)%n", TRANSIT, totalSec, totalMin);

        System.out.println("\n--- Distribuição por estado (Fila 1) ---");
        double sumP1 = 0.0;
        for (int s = 0; s <= CAP1; s++) {
            double prob = (totalMin > 0) ? (timeInState1[s] / totalMin) * 100.0 : 0.0;
            sumP1 += prob;
            System.out.printf("n1=%2d  tempo=%.2f s  P=%.2f%%%n", s, timeInState1[s] * 60.0, prob);
        }
        System.out.printf("Perdas F1: %d | soma P1 = %.2f%%%n", losses1, sumP1);

        System.out.println("\n--- Distribuição por estado (Fila 2) ---");
        double sumP2 = 0.0;
        for (int s = 0; s <= CAP2; s++) {
            double prob = (totalMin > 0) ? (timeInState2[s] / totalMin) * 100.0 : 0.0;
            sumP2 += prob;
            System.out.printf("n2=%2d  tempo=%.2f s  P=%.2f%%%n", s, timeInState2[s] * 60.0, prob);
        }
        System.out.printf("Perdas F2: %d | soma P2 = %.2f%%%n", losses2, sumP2);
    }
}
