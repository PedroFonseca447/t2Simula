import java.util.PriorityQueue;
import java.util.function.BiFunction;

public class Fila {

    // Parâmetros
    private double arrivalMin;
    private double arrivalMax;
    private int Servidores;
    private int Loss;
    private int Capacidade;  // capacidade total (em serviço + fila)
    private int Customers;   // clientes no sistema

    private final double minService;
    private final double maxService;

    // Estado
    private double clock = 0.0;                 // relógio da fila (min)
    private double nextArrival = Double.NaN;    // próxima chegada externa
    private final PriorityQueue<Double> departures = new PriorityQueue<>();

    // Estatística
    private final double[] times;               // tempo por estado 0..Capacidade (min)

    // Aleatórios
    private BiFunction<Double, Double, Double> uniform;

    public Fila(int Servidores, int Capacidade,
                double arrivalMin, double arrivalMax,
                double minService, double maxService) {
        this.Servidores = Servidores;
        this.Capacidade = Capacidade;
        this.arrivalMin = arrivalMin;
        this.arrivalMax = arrivalMax;
        this.minService = minService;
        this.maxService = maxService;
        this.times = new double[Capacidade + 1];
    }

    /** Inicializa fila. Se não tem chegada externa, nextArrival = +inf. */
    public void init(double t0, BiFunction<Double, Double, Double> uniform) {
        this.clock = t0;
        this.uniform = uniform;
        if (arrivalMin <= arrivalMax) {
            this.nextArrival = t0 + uniform.apply(arrivalMin, arrivalMax); // amostrada
        } else {
            this.nextArrival = Double.POSITIVE_INFINITY; // sem chegada externa
        }
    }

    /** Inicializa fila com PRIMEIRA chegada fixa em 'firstArrivalAt'. */
    public void initWithFirstArrival(double t0, BiFunction<Double, Double, Double> uniform, double firstArrivalAt) {
        this.clock = t0;
        this.uniform = uniform;
        if (arrivalMin <= arrivalMax) {
            this.nextArrival = firstArrivalAt; // <<< fixa a 1ª chegada
        } else {
            this.nextArrival = Double.POSITIVE_INFINITY;
        }
    }

    /** Chegada EXTERNA (agenda a próxima). */
    public void in() {
        nextArrival = clock + uniform.apply(arrivalMin, arrivalMax);
        tryEnterAndMaybeStart();
    }

    /** Chegada via ROTEAMENTO (não agenda próxima externa). */
    public void inFromRouting() {
        tryEnterAndMaybeStart();
    }

    private void tryEnterAndMaybeStart() {
        if (Customers < Capacidade) {
            int busyBefore = Math.min(Customers, Servidores);
            Customers++;
            if (Math.min(Customers, Servidores) > busyBefore) {
                departures.add(clock + uniform.apply(minService, maxService));
            }
        } else {
            Loss++;
        }
    }

    /** Término de serviço (somente se há término “agora”). */
    public void out() {
        if (Customers <= 0 || departures.isEmpty()) return;
        double top = departures.peek();
        if (top > clock + 1e-9) return; // stale/adiantado
        departures.poll();
        Customers--;
        if (Customers >= Servidores) {
            departures.add(clock + uniform.apply(minService, maxService));
        }
    }

    public double nextArrivalTime() { return nextArrival; }

    public double nextDepartureTime() {
        return departures.isEmpty() ? Double.POSITIVE_INFINITY : departures.peek();
    }

    /** Acumula tempo no estado atual até tNext (com clamp do índice). */
    public void advanceTo(double tNext) {
        if (tNext <= clock) { // nada a acumular
            clock = tNext;
            return;
        }
        int idx = Math.max(0, Math.min(Customers, Capacidade));
        times[idx] += (tNext - clock);
        clock = tNext;
    }

    public double now() { return clock; }

    public double[] getTimesByState() { return times; }

    public int getCustomers() { return Customers; }
    public int getLoss() { return Loss; }
    public int getCapacidade() { return Capacidade; }
    public int getServidores() { return Servidores; }

    // util opcional
    public void setChegadasExternasAtivas(boolean ativa) {
        if (!ativa) nextArrival = Double.POSITIVE_INFINITY;
    }
}
