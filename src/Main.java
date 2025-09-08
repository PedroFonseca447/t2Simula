import java.util.Locale;
import java.util.PriorityQueue;

public class Main {

    // ---------- RNG base ----------
    private static int previous = 1234;
    private static double nextRandom() {
        int a = 61, c = 37, M = 365789;
        previous = ((a * previous) + c) % M;
        return (double) previous / M; // [0,1)
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        // --------- Orçamento de aleatórios ----------
        final int COUNT = 100_000;
        double[] lista = new double[COUNT];
        for (int i = 0; i < COUNT; i++) lista[i] = nextRandom();
        final int[] pos = {0}; // próximo índice a consumir

        // --------- Helper: U(a,b) ----------
        java.util.function.BiFunction<Double, Double, Double> uniform = (a, b) -> {
            double u = lista[pos[0]++]; // aqui só chamaremos se já garantido pos[0] < COUNT
            return a + (b - a) * u;
        };

        // ============================ FILA 1 (Q1) ============================
        final double ARR1_A = 1.0, ARR1_B = 4.0; // interchegadas U(1,4)
        final double SVC1_A = 3.0, SVC1_B = 4.0; // serviço U(3,4)
        final int SERVERS1 = 2;
        final int QUEUE1 = 3;                    // capacidade da FILA (K)
        final int CAP1 = SERVERS1 + QUEUE1;      // capacidade total do sistema Q1

        // ============================ FILA 2 (Q2) ============================
        // Sem chegadas externas; recebe tudo que sai de Q1
        final double SVC2_A = 2.0, SVC2_B = 3.0; // serviço U(2,3)
        final int SERVERS2 = 1;
        final int QUEUE2 = 5;
        final int CAP2 = SERVERS2 + QUEUE2;

        // --------- Estado global ----------
        double t = 0.0; // relógio global (min)

        // --------- Estado Q1 ----------
        int n1 = 0; // clientes no sistema Q1
        double nextArr1 = 1.5; // primeira chegada em 1.5
        PriorityQueue<Double> depQ1 = new PriorityQueue<>(); // tempos de saída de Q1
        int losses1 = 0;
        double[] timeInState1 = new double[CAP1 + 1];

        // --------- Estado Q2 ----------
        int n2 = 0; // clientes no sistema Q2
        PriorityQueue<Double> depQ2 = new PriorityQueue<>(); // tempos de saída de Q2
        int losses2 = 0;
        double[] timeInState2 = new double[CAP2 + 1];

        // --------- Loop principal (parada limpa ao esgotar aleatórios) ----------
        while (true) {
            double nextDep1 = depQ1.isEmpty() ? Double.POSITIVE_INFINITY : depQ1.peek();
            double nextDep2 = depQ2.isEmpty() ? Double.POSITIVE_INFINITY : depQ2.peek();

            // Próximo evento entre: chegada em Q1, saída de Q1, saída de Q2
            double tNext = Math.min(nextArr1, Math.min(nextDep1, nextDep2));
            if (Double.isInfinite(tNext)) break;

            // Acumula tempos nos estados atuais
            if (n1 >= 0 && n1 <= CAP1) timeInState1[n1] += (tNext - t);
            if (n2 >= 0 && n2 <= CAP2) timeInState2[n2] += (tNext - t);
            t = tNext;

            if (t == nextArr1) {
                // ===================== CHEGADA EM Q1 =====================
                // Para agendar a próxima chegada, precisamos sortear: checar orçamento
                if (pos[0] >= COUNT) break;
                nextArr1 = t + uniform.apply(ARR1_A, ARR1_B);

                if (n1 < CAP1) {
                    int busyBefore = Math.min(n1, SERVERS1);
                    n1++;
                    // Se servidor ficou ocupado agora, inicia serviço (precisa sortear)
                    if (Math.min(n1, SERVERS1) > busyBefore) {
                        if (pos[0] >= COUNT) break;
                        depQ1.add(t + uniform.apply(SVC1_A, SVC1_B));
                    }
                } else {
                    // Sistema Q1 cheio → perda em Q1
                    losses1++;
                }

            } else if (t == nextDep1) {
                // ===================== SAÍDA DE Q1 (ROTEIA PARA Q2) =====================
                depQ1.poll();
                n1--;

                // Se ainda há fila em Q1, inicia novo serviço (precisa sortear)
                if (n1 >= SERVERS1) {
                    if (pos[0] >= COUNT) break;
                    depQ1.add(t + uniform.apply(SVC1_A, SVC1_B));
                }

                // Cliente tenta entrar em Q2
                if (n2 < CAP2) {
                    int busyBefore2 = Math.min(n2, SERVERS2);
                    n2++;
                    // Se servidor de Q2 ficou ocupado agora, inicia serviço (precisa sortear)
                    if (Math.min(n2, SERVERS2) > busyBefore2) {
                        if (pos[0] >= COUNT) break;
                        depQ2.add(t + uniform.apply(SVC2_A, SVC2_B));
                    }
                } else {
                    // Q2 cheia → perda em Q2
                    losses2++;
                }

            } else {
                // ===================== SAÍDA DE Q2 (vai embora do sistema) =====================
                depQ2.poll();
                n2--;

                // Se ainda há fila em Q2, inicia novo serviço (precisa sortear)
                if (n2 >= SERVERS2) {
                    if (pos[0] >= COUNT) break;
                    depQ2.add(t + uniform.apply(SVC2_A, SVC2_B));
                }
            }
        }

        // --------- Finais ---------
        double totalSimTimeMin = t;
        double totalSimTimeSec = totalSimTimeMin * 60.0;

        // --------- Relatório Q1 ---------
        System.out.println("Queue:   Fila 1 (G/G/" + SERVERS1 + "/" + QUEUE1 + ")");
        System.out.printf ("Chegadas (min): %.1f ... %.1f%n", ARR1_A, ARR1_B);
        System.out.printf ("Serviço   (min): %.1f ... %.1f%n", SVC1_A, SVC1_B);
        System.out.printf ("Servidores: %d | Capacidade total: %d | Tamanho fila: %d%n",
                SERVERS1, CAP1, QUEUE1);
        System.out.println("*********************************************************");
        System.out.printf("%6s %18s %23s%n", "Estado", "Tempo (sec)", "Probabilidade");

        double probSum1 = 0.0;
        for (int s = 0; s <= CAP1; s++) {
            double timeSec = timeInState1[s] * 60.0;
            double prob = (totalSimTimeMin > 0) ? (timeInState1[s] / totalSimTimeMin) * 100.0 : 0.0;
            probSum1 += prob;
            System.out.printf("%6d %18.2f %22.2f%%%n", s, timeSec, prob);
        }
        System.out.println();
        System.out.println("Perdas Fila 1: " + losses1);
        System.out.printf ("Tempo total de simulação: %.2f segundos%n", totalSimTimeSec);
        System.out.printf ("Sum of probabilities (Q1): %.2f%%%n", probSum1);
        System.out.println();

        // --------- Relatório Q2 ---------
        System.out.println("Queue:   Fila 2 (G/G/" + SERVERS2 + "/" + QUEUE2 + ")");
        System.out.println("Chegadas (min): — (somente de Fila 1)");
        System.out.printf ("Serviço   (min): %.1f ... %.1f%n", SVC2_A, SVC2_B);
        System.out.printf ("Servidores: %d | Capacidade total: %d | Tamanho fila: %d%n",
                SERVERS2, CAP2, QUEUE2);
        System.out.println("*********************************************************");
        System.out.printf("%6s %18s %23s%n", "Estado", "Tempo (sec)", "Probabilidade");

        double probSum2 = 0.0;
        for (int s = 0; s <= CAP2; s++) {
            double timeSec = timeInState2[s] * 60.0;
            double prob = (totalSimTimeMin > 0) ? (timeInState2[s] / totalSimTimeMin) * 100.0 : 0.0;
            probSum2 += prob;
            System.out.printf("%6d %18.2f %22.2f%%%n", s, timeSec, prob);
        }
        System.out.println();
        System.out.println("Perdas Fila 2: " + losses2);
        System.out.printf ("Sum of probabilities (Q2): %.2f%%%n", probSum2);
    }
}
