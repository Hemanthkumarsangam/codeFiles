package com.udacity.catpoint.security.service;

import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.application.StatusListener;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    private SecurityService securityService;
    private Sensor sensor;
    private final String sensorId = UUID.randomUUID().toString();

    @Mock
    private StatusListener statusListener;

    @Mock
    private ImageService imageService;

    @Mock
    private SecurityRepository securityRepository;

    private Sensor createSensor() {
        return new Sensor(sensorId, SensorType.DOOR);
    }

    private Set<Sensor> createSensors(int count, boolean active) {
        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Sensor s = new Sensor(UUID.randomUUID().toString(), SensorType.DOOR);
            s.setActive(active);
            sensors.add(s);
        }
        return sensors;
    }

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(securityRepository, imageService);
        sensor = createSensor();
    }

    @Test
    void systemArmed_sensorActivated_shouldSetPendingAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @Test
    void systemArmed_sensorActivated_pendingAlarm_shouldSetAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void pendingAlarm_allSensorsInactive_shouldSetNoAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void alarmActive_sensorStateChange_shouldNotAffectAlarm(boolean status) {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, status);
        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test
    void sensorAlreadyActive_inPending_shouldSetAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @ParameterizedTest
    @EnumSource(AlarmStatus.class)
    void sensorAlreadyInactive_shouldNotChangeAlarm(AlarmStatus status) {
        when(securityRepository.getAlarmStatus()).thenReturn(status);
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test
    void imageContainsCat_armedHome_shouldSetAlarm() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(image);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void imageDoesNotContainCat_allSensorsInactive_shouldSetNoAlarm() {
        Set<Sensor> sensors = createSensors(3, false);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.processImage(new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB));
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void systemDisarmed_shouldSetNoAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void systemArmed_shouldResetAllSensors(ArmingStatus status) {
        Set<Sensor> sensors = createSensors(3, true);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(status);
        for (Sensor s : sensors) {
            assertFalse(s.getActive());
        }
    }

    @Test
    void systemDisarmed_catDetected_thenArmedHome_shouldSetAlarm() {
        BufferedImage catImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        securityService.processImage(catImage);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void addAndRemoveListeners_shouldNotThrow() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
    }

    @Test
    void addAndRemoveSensors_shouldNotThrow() {
        securityService.addSensor(sensor);
        securityService.removeSensor(sensor);
    }

    @ParameterizedTest
    @EnumSource(AlarmStatus.class)
    void disarmed_sensorActivated_shouldNotChangeArming(AlarmStatus alarmStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(alarmStatus);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never()).setArmingStatus(any());
    }

    @Test
    void alarmActive_disarmed_shouldSetPending() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }
}
