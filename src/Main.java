import java.util.Locale;
import java.util.PriorityQueue;

public class Main {

    // ---------- RNG da tua base ----------
    private static int previous = 1234;
    private static double nextRandom() {
        int a = 61;
        int c = 37;
        int M = 365789;
        previous = ((a * previous) + c) % M;
        return (double) previous / M; // [0,1)
    }

    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        // --------- “Orçamento” de aleatórios ----------
        final int COUNT = 100_000;
        double[] lista = new double[COUNT];
        for (int i = 0; i < COUNT; i++) lista[i] = nextRandom();
        final int[] pos = {0}; // próximo índice a consumir

        // --------- Helpers para consumir do lista[] ----------
        java.util.function.BiFunction<Double, Double, Double> uniform = (a, b) -> {
            if (pos[0] >= COUNT) throw new RuntimeException("Sem mais números pseudoaleatórios.");
            double u = lista[pos[0]++];           // consome 1 número
            return a + (b - a) * u;               // U(a,b)
        };

        // --------- Parâmetros do sistema ----------
        final double ARR_A = 2.0, ARR_B = 5.0;    // interchegada (MIN..MAX) — em minutos
        final double SVC_A = 3.0, SVC_B = 5.0;    // serviço (MIN..MAX) — em minutos

        final int SERVERS  = 1;                   // <<< número de atendentes
        // CAPACITY = total de clientes no sistema (em serviço + na fila)
        final int CAPACITY = 6;                   // ex.: 2 em serviço + 5 na fila => estados 0..6 no outro exemplo deve mudar aqui

        // --------- Estado da simulação ----------
        double t = 0.0;                           // relógio (minutos)
        int n = 0;                                 // número de clientes no sistema (0..CAPACITY)

        double nextArr = 2.0;                     // 1ª chegada (minutos)
        PriorityQueue<Double> departures = new PriorityQueue<>(); // múltiplas saídas

        int losses = 0;                           // perdas por sistema cheio

        // --------- Estatística (tempo por estado) ----------
        double[] timeInState = new double[CAPACITY + 1]; // índices 0..CAPACITY

        // --------- Loop principal ----------
        while (pos[0] < COUNT) {
            double nextDep = departures.isEmpty() ? Double.POSITIVE_INFINITY : departures.peek();
            double tNext = Math.min(nextArr, nextDep);
            if (Double.isInfinite(tNext)) break;

            // acumula tempo no estado atual (n)
            timeInState[n] += (tNext - t);
            t = tNext;

            if (nextArr <= nextDep) {
                // ===== CHEGADA =====
                nextArr = t + uniform.apply(ARR_A, ARR_B);

                if (n < CAPACITY) {
                    // cliente entra
                    int busyBefore = Math.min(n, SERVERS);
                    n++;

                    // se ainda há servidor livre após a chegada, inicia serviço agora
                    if (Math.min(n, SERVERS) > busyBefore) {
                        departures.add(t + uniform.apply(SVC_A, SVC_B));
                    }
                } else {
                    // sistema cheio -> perda
                    losses++;
                }
            } else {
                // ===== SAÍDA (terminou o serviço do mais próximo) =====
                departures.poll(); // remove o término que ocorreu
                n--;

                // se ainda há fila esperando (n >= SERVERS), inicia novo serviço
                if (n >= SERVERS) {
                    departures.add(t + uniform.apply(SVC_A, SVC_B));
                }
            }
        }

        double totalSimTimeMin = t;        // em minutos
        double totalSimTimeSec = totalSimTimeMin * 60.0;

        // --------- Relatório ----------
        String queueLabel = "G/G/" + SERVERS + "/" + (CAPACITY - SERVERS); // G/G/c/K  (K = capacidade da fila)
        System.out.println("Queue:   Q1 (" + queueLabel + ")");
        System.out.printf ("Chegadas : %.1f ... %.1f%n", ARR_A, ARR_B);
        System.out.printf ("Tempo de serviço (min): %.1f ... %.1f%n", SVC_A, SVC_B);
        System.out.printf ("Serviços: %d | Capacidade mais nParticipantes da fila: %d | Tamanho fila: %d%n",
                SERVERS, CAPACITY, Math.max(0, CAPACITY - SERVERS));
        System.out.println("*********************************************************");
        System.out.printf("%6s %18s %23s%n", "Estado", "Tempo (sec)", "Probabilidade");

        double probSum = 0.0;
        for (int s = 0; s <= CAPACITY; s++) {
            double timeSec = timeInState[s] * 60.0;
            double prob = (totalSimTimeMin > 0) ? (timeInState[s] / totalSimTimeMin) * 100.0 : 0.0;
            probSum += prob;
            System.out.printf("%6d %18.2f %22.2f%%%n", s, timeSec, prob);
        }

        System.out.println();
        System.out.println("Perdas: " + losses);
        System.out.printf ("Tempo total de simulação: %.2f segundos%n", totalSimTimeSec);
        System.out.printf ("Sum of probabilities: %.2f%%%n", probSum);
        // Dica: tempo com exatamente 1 cliente (em segundos) = timeInState[1] * 60.0
    }
}
