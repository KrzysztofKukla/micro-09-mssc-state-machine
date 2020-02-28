package pl.kukla.krzys.msscssm.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import pl.kukla.krzys.msscssm.domain.Payment;
import pl.kukla.krzys.msscssm.domain.PaymentEvent;
import pl.kukla.krzys.msscssm.domain.PaymentState;
import pl.kukla.krzys.msscssm.repository.PaymentRepository;

/**
 * @author Krzysztof Kukla
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    public static final String PAYMENT_ID_HEADER = "payment_id";
    private final PaymentRepository paymentRepository;
    private final StateMachineFactory<PaymentState, PaymentEvent> stateMachineFactory;

    @Override
    public Payment newPayment(Payment payment) {
        payment.setPaymentState(PaymentState.NEW);
        return paymentRepository.save(payment);
    }

    @Override
    public StateMachine<PaymentState, PaymentEvent> preAuth(Long paymentId) {
        StateMachine<PaymentState, PaymentEvent> stateMachine = buildStateMachine(paymentId);
        sendEventMessage(paymentId, stateMachine, PaymentEvent.PRE_AUTHORIZE);
        return null;
    }

    @Override
    public StateMachine<PaymentState, PaymentEvent> authorizePayment(Long paymentId) {
        StateMachine<PaymentState, PaymentEvent> stateMachine = buildStateMachine(paymentId);
        sendEventMessage(paymentId, stateMachine, PaymentEvent.AUTH_APPROVED);
        return null;
    }

    @Override
    public StateMachine<PaymentState, PaymentEvent> declineAuth(Long paymentId) {
        StateMachine<PaymentState, PaymentEvent> stateMachine = buildStateMachine(paymentId);
        sendEventMessage(paymentId, stateMachine, PaymentEvent.AUTH_DECLINED);
        return null;
    }

    private void sendEventMessage(Long paymentId, StateMachine<PaymentState, PaymentEvent> stateMachine, PaymentEvent paymentEvent) {
        Message<PaymentEvent> eventMessage = MessageBuilder.withPayload(paymentEvent)
            //here we are enriching (wzbogacamy) this message with paymentId, because it will be needed for us
            .setHeader(PAYMENT_ID_HEADER, paymentId)
            .build();

        stateMachine.sendEvent(eventMessage);
    }

    private StateMachine<PaymentState, PaymentEvent> buildStateMachine(Long paymentId) {
        Payment payment = paymentRepository.getOne(paymentId);
        StateMachine<PaymentState, PaymentEvent> stateMachine = stateMachineFactory.getStateMachine(paymentId.toString());

        //here we stop stateMachine
        stateMachine.stop();
        //and then we are setting stateMachine to specific state of payment retrieved from database
        stateMachine.getStateMachineAccessor()
            .doWithAllRegions(stateMachineAccessor -> {
                stateMachineAccessor.resetStateMachine(new DefaultStateMachineContext<>(payment.getPaymentState(), null, null, null));
            });
        stateMachine.start();
        return stateMachine;

    }

}
