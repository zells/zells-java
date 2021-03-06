package org.zells.dish.network.encoding.implementations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.zells.dish.delivery.Address;
import org.zells.dish.delivery.Delivery;
import org.zells.dish.delivery.Message;
import org.zells.dish.delivery.messages.*;
import org.zells.dish.network.connecting.Packet;
import org.zells.dish.network.Signal;
import org.zells.dish.network.encoding.Encoding;
import org.zells.dish.network.signals.*;
import org.zells.dish.util.Uuid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MsgpackEncoding implements Encoding {

    private final ObjectMapper objectMapper;

    public MsgpackEncoding() {
        objectMapper = new ObjectMapper(new MessagePackFactory());
    }

    public Packet encode(Signal signal) {
        try {
            return new Packet(objectMapper.writeValueAsBytes(deflate(signal)));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Signal decode(Packet packet) {
        try {
            Object payload = objectMapper.readValue(packet.getBytes(), new TypeReference<List<Object>>() {
            });
            if (!(payload instanceof List)) {
                throw new RuntimeException("invalid format");
            }
            return inflate((List) payload);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Object deflate(Signal signal) {
        List<Object> payload = new ArrayList<Object>();

        if (signal instanceof OkSignal) {
            payload.add("OK");
        } else if (signal instanceof FailedSignal) {
            payload.add("FAILED");
            payload.add(((FailedSignal) signal).getCause());
        } else if (signal instanceof DeliverSignal) {
            Delivery delivery = ((DeliverSignal) signal).getDelivery();

            payload.add("DELIVER");
            payload.add(delivery.getUuid().getBytes());
            payload.add(delivery.getReceiver().toBytes());
            payload.add(deflateMessage(delivery.getMessage()));
        } else if (signal instanceof JoinSignal) {
            payload.add("JOIN");
        } else if (signal instanceof LeaveSignal) {
            payload.add("LEAVE");
        } else {
            throw new RuntimeException("unsupported signal type: " + signal.getClass());
        }

        return payload;
    }

    private Signal inflate(List payload) {
        if (payload.isEmpty()) {
            throw new RuntimeException("invalid format");
        }

        if (payload.get(0).equals("OK")) {
            return new OkSignal();
        } else if (payload.get(0).equals("FAILED")) {
            if (payload.size() == 1) {
                return new FailedSignal();
            } else {
                return new FailedSignal((String) payload.get(1));
            }
        } else if (payload.get(0).equals("DELIVER")) {
            if (payload.size() != 4) {
                throw new RuntimeException("invalid format");
            }

            return new DeliverSignal(new Delivery(
                    new Uuid((byte[]) payload.get(1)),
                    Address.fromBytes((byte[]) payload.get(2)),
                    inflateMessage(payload.get(3))
            ));
        } else if (payload.get(0).equals("JOIN")) {
            return new JoinSignal();
        } else if (payload.get(0).equals("LEAVE")) {
            return new LeaveSignal();
        } else {
            throw new RuntimeException("unsupported signal: " + payload.get(0));
        }
    }

    private Object deflateMessage(Message message) {
        if (message instanceof NullMessage) {
            return null;
        } else if (message instanceof StringMessage) {
            return message.asString();
        } else if (message instanceof BooleanMessage) {
            return message.isTrue();
        } else if (message instanceof IntegerMessage) {
            return message.asInteger();
        } else if (message instanceof BinaryMessage) {
            return prefixed(0, message.asBytes());
        } else if (message instanceof AddressMessage) {
            return prefixed(1, message.asBytes());
        } else if (message instanceof CompositeMessage) {
            HashMap<String, Object> map = new HashMap<String, Object>();
            for (String key : message.keys()) {
                map.put(key, deflateMessage(message.read(key)));
            }
            return map;
        }

        throw new RuntimeException("unsupported message type: " + message.getClass());
    }

    private Object prefixed(int prefix, byte[] bytes) {
        byte[] prefixed = new byte[bytes.length + 1];
        prefixed[0] = (byte) prefix;
        System.arraycopy(bytes, 0, prefixed, 1, bytes.length);
        return prefixed;
    }

    private byte[] unPrefix(byte[] bytes) {
        byte[] slice = new byte[bytes.length - 1];
        System.arraycopy(bytes, 1, slice, 0, bytes.length - 1);
        return slice;
    }

    private Message inflateMessage(Object object) {
        if (object == null) {
            return new NullMessage();
        } else if (object instanceof String) {
            return new StringMessage((String) object);
        } else if (object instanceof Boolean) {
            return new BooleanMessage((Boolean) object);
        } else if (object instanceof Integer) {
            return new IntegerMessage((Integer) object);
        } else if (object instanceof byte[]) {
            byte[] bytes = (byte[]) object;
            if (bytes[0] == 0) {
                return new BinaryMessage(unPrefix(bytes));
            } else if (bytes[0] == 1) {
                return new AddressMessage(Address.fromBytes(unPrefix(bytes)));
            }
        } else if (object instanceof Map) {
            CompositeMessage message = new CompositeMessage();
            for (Object key : ((Map) object).keySet()) {
                message.put((String) key, inflateMessage(((Map) object).get(key)));
            }
            return message;
        }

        throw new RuntimeException("unsupported message type");
    }
}
