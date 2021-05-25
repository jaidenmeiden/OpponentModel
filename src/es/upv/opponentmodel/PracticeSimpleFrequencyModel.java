package es.upv.opponentmodel;

import java.util.*;
import java.util.stream.Collectors;

import genius.core.Bid;
import genius.core.boaframework.BOAparameter;
import genius.core.boaframework.NegotiationSession;
import genius.core.boaframework.OpponentModel;
import genius.core.issue.Objective;
import genius.core.issue.Value;
import genius.core.utility.AdditiveUtilitySpace;
import negotiator.boaframework.opponentmodel.tools.UtilitySpaceAdapter;

// Los modelos de preferencias del oponente, en Genius se implementan heredando
// de la clase OpponentModel.
public class PracticeSimpleFrequencyModel extends OpponentModel {
	
	/*
	 * Define aquí los atributos de la clase y el objeto que necesites
	 */
	Bid lastOffert;
	int countOffers;
	double delta;
	List<Integer> issueIds;
	Map<Integer, Double> weights; // issueId: weight
	Map<Integer, Map<Value, Integer>> values; // issueId: [value: contador]


//	El método getName devuelve una cadena de texto con la descripción del componente.
//	Aunque no lo modifiques en esta práctica, tenlo en cuenta para cuando implementes el
//	proyecto.
	@Override
	public String getName() {
		return "Jaiden Meiden (Practica)";
	}

//	El método init se ejecuta una ´unica vez antes de la negociación y sirve para inicializar el
//	modelo de preferencias del oponente. Aquí deberías de inicializar todos los atributos y
//	variables que necesites para el modelo. Toma como entrada información de la negociación
//	que se va a llevar a cabo, así como un diccionario con parámetros para inicializar el
//	componente.
	@Override
	public void init(NegotiationSession negotiationSession, Map<String, Double> parameters) {
		super.init(negotiationSession, parameters);

		/*En esta parte del código debes poner cualquier instrucción que necesites
		 * para la inicialización del modelo de aprendizaje
		 */
		countOffers = 0;
		delta = parameters.get("delta");
		weights = new HashMap<>();
		values = new HashMap<>();
		issueIds = negotiationSession.getIssues().stream().map(Objective::getNumber).collect(Collectors.toList());
		int issueNumbers = issueIds.size();
		for (int issueId : issueIds) {
			weights.put(issueId, 1.0 / issueNumbers);
			values.put(issueId, null);
		}
	}

//	El método updateModel es donde se actualiza el modelo de las preferencias del oponente.
//	Este método recibe como entrada una oferta recibida del oponente, y el momento de la
//	negociación en el que se recibió esa oferta.
	@Override
	protected void updateModel(Bid bid, double time) {
		 /*
		  * En esta parte del código debes poner la lógica a realizar por el modelo
		  * cuando una oferta del oponente es recibida
		  */

		// Actualizar el número de ofertas recibidas,
		// así como la última oferta recibida del oponente.
		lastOffert = bid;
		countOffers++;

		for (Map.Entry<Integer, Value> entry : lastOffert.getValues().entrySet()) {
			int issueId = entry.getKey();
			Value bidValue = entry.getValue();
			// Actualizar los pesos wi
			// El peso para un atributo i únicamente debería actualizarse si el valor ofrecido
			// por el oponente en ese atributo NO ha cambiado con respecto a la última oferta
			// recibida.
			if (lastOffert.getValue(issueId) == bidValue) {
				double oldWeight = weights.get(issueId);
				weights.put(issueId, oldWeight + delta);
			}
			// Cuenta de cuántas veces ha aparecido cada
			// valor de atributo en las ofertas del oponente y
			// debera actualizarse siempre.
			Map<Value, Integer> valueCounter = values.get(issueId);
			Integer valueCount = null;
			if (valueCounter != null) {
				valueCount = valueCounter.get(bidValue);
			}
			if (valueCount != null) {
				valueCounter.put(bidValue, valueCount + 1);
			} else {
				valueCounter = new HashMap<>();
				valueCounter.put(bidValue, 1);
				values.put(issueId, valueCounter);
			}
		}
		
	}

//	El método getBidEvaluation es el método que usan el resto de componentes BOA para
//	obtener la utilidad de una oferta para el oponente. Por tanto, aquí se toma como entrada
//	la oferta sobre la cual debemos estimar la utilidad para el oponente.
	@Override
	public double getBidEvaluation(Bid bid) {
		/*
		 * En esta parte del modelo debes poner la lógica empleada para que, dada una oferta,
		 * se utilice el modelo de las preferencias del oponente para determinar su utilidad estimada
		 */
		double utility = 0;

		// Calculamos la suma del total de los pesos wi
		// Este paso es necesario ya que al ir sumando el valor delta,
		// los pesos pueden haber cambiado su escala de 0 a 1. Usaremos
		// esta suma de pesos para normalizar los pesos una vez lo necesitemos.
		double weightsSum = weights.values().stream().mapToDouble(Double::doubleValue).sum();

		// Escalar los pesos (si es necesario)
		if (weightsSum > 1) {
			for (Map.Entry<Integer, Double> entry : weights.entrySet()) {
				int issueId = entry.getKey();
				double weight = entry.getValue();
				weight /= weightsSum;
				weights.put(issueId, weight);
			}
		}

		// Para cada atributo de la oferta a evaluar, se obtiene el número de veces que ha aparecido
		// el valor de dicho atributo en las ofertas enviadas por el oponente. La estimación de Vi
		// para dicho atributo será calculada de acuerdo a la fórmula: ** V_i_t(X_i) = #X_i/r **.
		// Donde:
		// ** #X_i ** representa el número de veces que el valor Xi ha sido recibido del oponente en el histórico de la negociación
		// ** r ** representa el número total de ofertas recibidas del oponente


		// Obtener contadores de los valores, actualizar V_i_t y calcular la utilidad
		for (Map.Entry<Integer, Map<Value, Integer>> entry : values.entrySet()) {
			int issueId = entry.getKey();
			Map<Value, Integer> value = entry.getValue();

			if (value != null) {
				Integer valueCount = value.get(bid.getValue(issueId));
				if (valueCount != null) {
					double weight = weights.get(issueId);
					// V_i_t(X_i) = #X_i/r
					double function = (double) valueCount / countOffers;
					// Acumulamos la utilidad aportada por la oferta como la suma acumulada del
					// peso normalizado del atributo por la estimación de V_i_t.
					// Formula: U(X) = Sumatoria(W_i * V_i_t(X_i))
					utility += weight * function;
				}
			}
		}

		return utility;
	}

//  (No hay que modificarlo) Devuelve un objeto de tipo AdditiveUtilitySpace que utiliza el getBidEvaluation
	@Override
	public AdditiveUtilitySpace getOpponentUtilitySpace() {
		return new UtilitySpaceAdapter(this, this.negotiationSession.getDomain());
	}

//	El método getParameterSpec devuelve un Set que describe los parámetros que necesita la
//	componente para inicializarse. Se utiliza para que desde la interfaz de Genius se puedan
//	saber qué parámetros se necesitan para usar el componente.
	@Override
	public Set<BOAparameter> getParameterSpec(){
		Set<BOAparameter> set = new HashSet<BOAparameter>();
		/* Aquí describe los parámetros que necesita el algoritmo de aprendizaje. Ejemplos:
			set.add(new BOAparameter("n", 20.0, "The number of own best offers to be used for genetic operations"));
			set.add(new BOAparameter("n_opponent", 20.0, "The number of opponent's best offers to be used for genetic operations"));
		*/

		set.add(new BOAparameter("delta", 0.001, "Weight increment"));
		return set;
	}

}
