package com.udasecurity.security.service;

import com.udasecurity.image.service.ImageService;
import com.udasecurity.security.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    @Mock
    private SecurityRepository repository;

    @Mock
    private ImageService imageService;

    @InjectMocks
    private SecurityService securityService;

    private Sensor door;
    private Sensor window;

    @BeforeEach
    void setup() {
        door = new Sensor("EntryPoint", SensorType.DOOR);
        window = new Sensor("WindowPoint", SensorType.WINDOW);
    }

    @Test
    void systemArmed_whenSensorActivated_thenPendingAlarm() {
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(door, true);
        verify(repository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @Test
    void pendingAlarm_whenSensorActivated_thenAlarmTriggered() {
        Sensor door = new Sensor("door", SensorType.DOOR);
        door.setActive(false);
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(repository.getSensors()).thenReturn(Set.of(door));
        securityService.changeSensorActivationStatus(door, true);
        verify(repository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void pendingAlarm_whenAllSensorsDeactivated_thenNoAlarm() {
        door.setActive(true);
        Set<Sensor> sensors = new HashSet<>(Set.of(door));
        when(repository.getSensors()).thenReturn(sensors);
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(door, false);
        verify(repository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void alarmActive_whenSensorStateChanges_thenIgnoreChanges() {
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(door, true);
        verify(repository, never()).setAlarmStatus(any());
    }

    @Test
    void activeSensorAndPendingAlarm_whenReactivated_thenAlarm() {
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(repository.getSensors()).thenReturn(Set.of(door));

        securityService.changeSensorActivationStatus(door, true);
        verify(repository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void inactiveSensor_whenDeactivated_thenNoChange() {
        door.setActive(false);
        securityService.changeSensorActivationStatus(door, false);
        verify(repository, never()).setAlarmStatus(any());
    }

    @Test
    void catDetectedWhileHomeArmed_thenTriggerAlarm() {
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(image);

        verify(repository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void noCatAndNoSensors_thenSetNoAlarm() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        when(repository.getSensors()).thenReturn(Set.of(door));

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(image);

        verify(repository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"ALARM", "PENDING_ALARM"})
    void disarmingSystem_shouldClearAlarm(AlarmStatus status) {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(repository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void armingSystem_shouldResetSensors(ArmingStatus status) {
        door.setActive(true);
        when(repository.getSensors()).thenReturn(Set.of(door));

        securityService.setArmingStatus(status);

        assertFalse(door.getActive());
        verify(repository).updateSensor(door);
    }

    @Test
    void imageHasCat_disarmThenArmHome_shouldRaiseAlarm() {
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(image);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(repository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void multipleSensorsActivated_thenOneDeactivated_shouldStayAlarmed() {
        when(repository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM, AlarmStatus.PENDING_ALARM);
        when(repository.getSensors()).thenReturn(Set.of(door, window));

        securityService.changeSensorActivationStatus(door, true);
        securityService.changeSensorActivationStatus(window, true);

        verify(repository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
        verify(repository).setAlarmStatus(AlarmStatus.ALARM);

        securityService.changeSensorActivationStatus(door, false);
        verify(repository, times(2)).setAlarmStatus(any());
    }

    @Test
    void detectCatThenNoCatAndNoSensors_thenClearAlarm() {
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(repository.getSensors()).thenReturn(Set.of());
        when(imageService.imageContainsCat(any(), anyFloat()))
                .thenReturn(true)
                .thenReturn(false);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(image);
        securityService.processImage(image);
        verify(repository).setAlarmStatus(AlarmStatus.ALARM);
        verify(repository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void catDetectionWhileDisarmed_shouldNotSetAlarm() {
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(image);

        verify(repository, never()).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void catDetected_thenSensorActivated_thenNoCat_shouldRemainAlarmed() {
        when(repository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true).thenReturn(false);
        when(repository.getSensors()).thenReturn(Set.of(door));

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        securityService.processImage(image);
        door.setActive(true);
        securityService.changeSensorActivationStatus(door, true);
        securityService.processImage(image);

        verify(repository, atLeastOnce()).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void sensorsShouldReset_whenDisarmedThenArmed() {
        door.setActive(true);
        window.setActive(true);
        Set<Sensor> sensors = Set.of(door, window);

        when(repository.getSensors()).thenReturn(sensors);

        securityService.setArmingStatus(ArmingStatus.DISARMED);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        for (Sensor sensor : sensors) {
            assertFalse(sensor.getActive());
            verify(repository, atLeastOnce()).updateSensor(sensor);
        }
    }
}
